package org.crumb.be.user.service;

import org.crumb.be.user.entity.User;

public interface UserService {
    User findByEmail(String email);
    User save(User user);
}
