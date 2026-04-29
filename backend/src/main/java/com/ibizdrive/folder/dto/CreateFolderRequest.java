package com.ibizdrive.folder.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * {@code POST /api/folders} 요청 본문 (docs/02 §7.5).
 *
 * <p>shape: {@code { parentId?: UUID, name: string, auditLevel?: 'standard'|'strict' }}.
 *
 * <p>{@code parentId == null}이면 root 폴더 생성 — controller SpEL이 ROLE ADMIN으로 분기 (ADR #30).
 * {@code auditLevel}은 NULL 허용 — controller가 service에 default {@code "standard"}로 전달.
 *
 * <p>{@code ownerId}는 본 본문에 받지 않음 — controller가 인증된 principal에서 추출하여 service에 전달
 * (보안: 사용자가 임의 ownerId를 지정하지 못하도록).
 *
 * <p>{@code name}의 정규화/충돌 검증은 service({@link com.ibizdrive.folder.FolderMutationService#create})가 수행.
 * controller는 길이/공백 1차 가드만 적용해 명백히 잘못된 입력을 service 진입 전에 차단.
 */
public record CreateFolderRequest(
    UUID parentId,
    @NotBlank @Size(max = 255) String name,
    String auditLevel
) {}
