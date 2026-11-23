package com.cloudhub.cloud;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OAuthStateRepository extends JpaRepository<OAuthState, String> {}
