// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.persistence;

import java.util.List;
import org.springframework.data.repository.ListCrudRepository;

public interface UserAccountRepository extends ListCrudRepository<UserAccount, String> {

    List<UserAccount> findAllByOrderByUsernameAsc();

    long countByAdminTrue();
}
