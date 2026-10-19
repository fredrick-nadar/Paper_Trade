package com.fredrick.papertrade;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// One-time fill for accounts created before User.publicId existed; a no-op once all rows have one.
@Component
public class PublicIdBackfill implements ApplicationRunner {

    private final UserRepository users;

    public PublicIdBackfill(UserRepository users) {
        this.users = users;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        users.findByPublicIdIsNull().forEach(User::assignPublicId);
    }
}
