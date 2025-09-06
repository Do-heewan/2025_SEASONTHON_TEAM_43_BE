package org.crumb.be.service;

import org.crumb.be.client.User;

public interface UserService {
    User findByEmail(String email);
    User save(User user);
}
