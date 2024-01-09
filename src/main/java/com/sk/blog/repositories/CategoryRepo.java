package com.sk.blog.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sk.blog.entities.Category;

public interface CategoryRepo extends JpaRepository<Category, Integer> {

}
