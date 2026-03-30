package com.taskflow.repository;

import com.taskflow.model.Role;
import com.taskflow.model.MemberType;
import com.taskflow.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    // Can log in using either username or email (front-end may send either)
    Optional<User> findByUsernameOrEmail(String username, String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    List<User> findByRoleIn(List<Role> roles);

    List<User> findByMemberTypeIn(List<MemberType> types);
}
