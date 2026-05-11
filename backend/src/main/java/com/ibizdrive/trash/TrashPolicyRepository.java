package com.ibizdrive.trash;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Single-row policy table. Service는 항상 {@link TrashPolicy#SINGLETON_ID}로 접근한다.
 */
public interface TrashPolicyRepository extends JpaRepository<TrashPolicy, Short> {
}
