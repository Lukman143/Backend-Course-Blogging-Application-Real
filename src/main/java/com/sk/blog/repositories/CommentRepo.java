package com.sk.blog.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sk.blog.entities.Comment;

public interface CommentRepo  extends JpaRepository<Comment	, Integer> {

}
