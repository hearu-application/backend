package com.example.hearu.user.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.hearu.user.domain.User;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
}