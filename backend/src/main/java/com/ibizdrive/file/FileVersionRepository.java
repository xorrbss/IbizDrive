package com.ibizdrive.file;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FileVersionRepository extends JpaRepository<FileVersion, UUID> {
}
