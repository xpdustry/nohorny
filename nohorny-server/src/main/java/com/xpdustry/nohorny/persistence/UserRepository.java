// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<UserAccount, String> {}
