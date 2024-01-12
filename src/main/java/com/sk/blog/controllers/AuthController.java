
package com.sk.blog.controllers;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import javax.validation.Valid;

import com.sk.blog.twilio.TwilioConfig;
import org.springframework.mail.SimpleMailMessage;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.mail.javamail.JavaMailSender;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sk.blog.entities.User;
import com.sk.blog.exceptions.ApiException;
import com.sk.blog.payloads.JwtAuthRequest;
import com.sk.blog.payloads.JwtAuthResponse;
import com.sk.blog.payloads.UserDto;
import com.sk.blog.repositories.UserRepo;
import com.sk.blog.security.JwtTokenHelper;
import com.sk.blog.services.UserService;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;

@RestController
@RequestMapping("/api/v1/auth/")
public class AuthController {

	@Autowired
	private JwtTokenHelper jwtTokenHelper;

	@Autowired
	private UserDetailsService userDetailsService;

	@Autowired
	private AuthenticationManager authenticationManager;

	@Autowired
	private UserService userService;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private JavaMailSender javaMailSender;

	@Autowired
	private TwilioConfig twilioConfig;

	@PostMapping("/login")
	public ResponseEntity<JwtAuthResponse> createToken(@RequestBody JwtAuthRequest request) throws Exception {
		this.authenticate(request.getUsername(), request.getPassword());
		UserDetails userDetails = this.userDetailsService.loadUserByUsername(request.getUsername());
		String token = this.jwtTokenHelper.generateToken(userDetails);
		JwtAuthResponse response = new JwtAuthResponse();
		response.setToken(token);
		response.setUser(this.mapper.map((User) userDetails, UserDto.class));
		return new ResponseEntity<JwtAuthResponse>(response, HttpStatus.OK);
	}

	private void authenticate(String username, String password) throws Exception {

		UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(username,
				password);

		try {

			this.authenticationManager.authenticate(authenticationToken);

		} catch (BadCredentialsException e) {
			System.out.println("Invalid Detials !!");
			throw new ApiException("Invalid username or password !!");
		}

	}

	// register new user api

	@PostMapping("/register")
	public ResponseEntity<UserDto> registerUser(@Valid @RequestBody UserDto userDto) {
		UserDto registeredUser = this.userService.registerNewUser(userDto);
		return new ResponseEntity<UserDto>(registeredUser, HttpStatus.CREATED);
	}

	// get logged in user data

	@Autowired
	private UserRepo userRepo;

	@Autowired
	private ModelMapper mapper;

	@GetMapping("/current-user/")
	public ResponseEntity<UserDto> getUser(Principal principal) {
		User user = this.userRepo.findByEmail(principal.getName()).get();
		return new ResponseEntity<UserDto>(this.mapper.map(user, UserDto.class), HttpStatus.OK);
	}
//{
//  "email": "user@example.com"
//}
	@PostMapping("/reset-password/request")
	public ResponseEntity<?> requestResetPassword(@RequestBody Map<String, String> requestMap) {
		// Check if the email exists in the database
		String email = requestMap.get("email");
		if (email != null) {
			Optional<User> optionalUser = userRepo.findByEmail(email);
			if (optionalUser.isPresent()) {
				User user = optionalUser.get();

				// Generate a reset token (you may want to use a library for this)
				String resetToken = RandomStringUtils.randomAlphanumeric(6);
				// Set/reset token and expiration time in the database
				user.setResetToken(resetToken);
				userRepo.save(user);

				// Send the reset link or token to the user's email (you need to implement this)
				sendResetPasswordEmail(user.getEmail(), resetToken);
				//sendResetPasswordOTP("9890448392",resetToken);

				return new ResponseEntity<>("Password reset request received. Check your email for instructions.", HttpStatus.OK);
			} else {
				return new ResponseEntity<>("No user found with the provided email address.", HttpStatus.NOT_FOUND);
			}
		} else {
			return new ResponseEntity<>("Email address is required.", HttpStatus.BAD_REQUEST);
		}
	}

	private void sendResetPasswordEmail(String toEmail, String resetToken) {
		SimpleMailMessage mailMessage = new SimpleMailMessage();
		mailMessage.setTo(toEmail);
		mailMessage.setSubject("Password Reset Request");
		mailMessage.setText("Please reset your password, taking the following OTP: "+ resetToken);
		javaMailSender.send(mailMessage);
	}


	public void sendResetPasswordOTP(String mobileNumber, String resetToken) {
		// Initialize the Twilio client
		Twilio.init(twilioConfig.getAccountSid(), twilioConfig.getAuthToken());
//		Spring Boot to send SMS messages using FMS (Firebase Cloud Messaging) this used in other project in org
		// Construct the message body
		String otpMessage = "Your OTP for password reset is: " + resetToken;

		try {
			// Send the SMS using Twilio API
			Message message = Message.creator(
							new com.twilio.type.PhoneNumber("+91" + mobileNumber), // Replace with the user's mobile number
							new com.twilio.type.PhoneNumber(twilioConfig.getPhoneNumber()),
							otpMessage)
					.create();

			System.out.println("SMS sent with SID: " + message.getSid());
		} catch (Exception e) {
			// Handle exceptions appropriately
			e.printStackTrace();
		}
	}




	// Reset password with token
//	{
//		"resetToken": "your_reset_token_here",
//			"newPassword": "new_password_here"
//	}
	@PostMapping("/reset-password/confirm")
	public ResponseEntity<?> resetPassword(@RequestBody Map<String, String> requestMap) {
		// Find user by reset token
		String resetToken = requestMap.get("resetToken");
		if (resetToken != null) {
			Optional<User> optionalUser = userRepo.findByResetToken(resetToken);
			if (optionalUser.isPresent()) {
				User user = optionalUser.get();

				// Update the password and reset token
				user.setPassword(passwordEncoder.encode(requestMap.get("newPassword")));
				user.setResetToken(null);
				userRepo.save(user);

				return new ResponseEntity<>("Password successfully reset.", HttpStatus.OK);
			} else {
				return new ResponseEntity<>("Invalid reset token.", HttpStatus.BAD_REQUEST);
			}
		} else {
			return new ResponseEntity<>("Reset token is required.", HttpStatus.BAD_REQUEST);
		}
	}
}
