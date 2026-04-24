# 03 - 보안 & 컴플라이언스

> 권한 모델, 감사 정책, 저장소 보안, 법적 보존(Legal Hold) 등 보안 관점 설계.
> **현재 상태**: 스켈레톤 (팀 합류 후 본문 작성)

---

## 1. 위협 모델 (Threat Model)

### 1.1 보호 대상 자산

- [ ] 문서 기밀성 (Confidentiality)
- [ ] 문서 무결성 (Integrity)
- [ ] 가용성 (Availability)
- [ ] 감사 증적 (Audit trail)

### 1.2 위협 시나리오

- [ ] 내부자 무단 접근
- [ ] 외부 공격자 (phishing, credential stuffing)
- [ ] 권한 상승 (Privilege escalation)
- [ ] 경로 추측 공격 (Path traversal)
- [ ] 악성 파일 업로드
- [ ] 감사 로그 변조

---

## 2. 인증 (Authentication)

### 2.1 기본 인증

- [ ] 방식: SSO (SAML / OIDC) 또는 자체 ID/PW + MFA
- [ ] 세션 관리: JWT (짧은 만료) + refresh token (HTTP-only cookie)
- [ ] 로그인 실패 제한: 5회 / 15분, 이후 captcha
- [ ] 비활성 세션 타임아웃: 30분

### 2.2 서비스 계정

- [ ] 관리자 도구용 별도 권한
- [ ] 감사 로그는 서비스 계정이어도 기록

---

## 3. 권한 모델 (단일 진실 출처)

### 3.1 Preset 정의

| Preset | read | upload | edit | delete | download | move | share | admin |
|---|---|---|---|---|---|---|---|---|
| **read** | ✅ | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ | ❌ |
| **upload** | ✅ | ✅ | ❌ | ❌ | ✅ | ❌ | ❌ | ❌ |
| **edit** | ✅ | ✅ | ✅ | ✅ (자기 것) | ✅ | ✅ | ❌ | ❌ |
| **admin** | ✅ | ✅ | ✅ | ✅ (전체) | ✅ | ✅ | ✅ | ✅ |

### 3.2 Subject 유형

- [ ] user: 개인
- [ ] department: 부서 (LTREE 기반 하위 부서 포함 옵션)
- [ ] role: 역할 (member/admin/auditor)
- [ ] everyone: 전사

### 3.3 권한 상속

- [ ] 자식 폴더/파일은 부모 권한 상속 (default)
- [ ] 자식에서 명시적 권한 → override
- [ ] 계산 로직: 재귀 CTE (최상위 root까지 순회, deny 우선)

### 3.4 권한 매트릭스 (엔드포인트 × 권한)

- [ ] 모든 엔드포인트에 요구 권한 명시
- [ ] 백엔드 미들웨어 `requirePermission(['edit'])` 예시 작성
- [ ] 403 응답 기준 문서화

---

## 4. 감사 정책 (Audit Policy)

### 4.1 감사 이벤트 타입

```ts
type AuditEventType =
  // 파일
  | 'file.viewed'        // strict audit_level 폴더만
  | 'file.downloaded'
  | 'file.uploaded'
  | 'file.renamed'
  | 'file.moved'
  | 'file.deleted'
  | 'file.restored'
  | 'file.purged'
  // 버전
  | 'version.created'
  | 'version.restored'
  | 'version.downloaded'
  // 폴더
  | 'folder.created'
  | 'folder.renamed'
  | 'folder.moved'
  | 'folder.deleted'
  | 'folder.restored'
  | 'folder.audit_level_changed'
  // 권한 / 공유
  | 'permission.granted'
  | 'permission.revoked'
  | 'permission.changed'
  | 'share.created'
  | 'share.revoked'
  | 'share.expired'
  // 인증
  | 'user.login.success'
  | 'user.login.failed'
  | 'user.logout'
  | 'user.password.changed'
  | 'user.mfa.enabled'
  // 관리자
  | 'admin.user.created'
  | 'admin.user.updated'
  | 'admin.user.deactivated'
  | 'admin.role.changed'
  | 'admin.quota.changed'
  | 'admin.legal_hold.placed'
  | 'admin.legal_hold.released'
  // 시스템
  | 'system.backup.completed'
  | 'system.purge.executed'
```

### 4.2 감사 레벨 정책

```text
folders.audit_level:
  'standard': upload/download/move/delete/permission 이벤트만 기록
  'strict':   위 + view/preview 이벤트도 기록

→ 관리자가 민감 폴더에 audit_level='strict' 지정
→ 일반 폴더는 view 로그 생략 (폭증 방지)
```

### 4.3 감사 로그 보존

- [ ] 기본 보존 기간: 3년 (도메인에 따라 조정)
- [ ] Legal Hold 대상: 무기한
- [ ] 파티션별 아카이빙 (cold storage 이전)

### 4.4 감사 로그 불변성 강제

- [ ] DB 레벨 REVOKE UPDATE, DELETE (02 문서 §2.8)
- [ ] application role과 admin role 분리
- [ ] 파티션 분리 + WORM storage (v1.x)

---

## 5. 저장소 보안

### 5.1 전송 구간

- [ ] TLS 1.2+ 전체 강제
- [ ] HSTS 헤더
- [ ] presigned URL 사용 시 짧은 만료 (10분 이내)

### 5.2 저장 구간

- [ ] S3 서버 사이드 암호화 (SSE-S3 또는 SSE-KMS)
- [ ] KMS 키 로테이션 주기: 1년
- [ ] 버킷 public access 차단

### 5.3 악성 파일 대응

- [ ] 확장자 화이트리스트 (02 문서 §5.4)
- [ ] MIME magic number 검증
- [ ] 바이러스 스캔 (v1.x)
- [ ] 스캔 실패 시 다운로드 경고

### 5.4 다운로드 보안

- [ ] Content-Disposition: attachment 강제
- [ ] `X-Content-Type-Options: nosniff`
- [ ] HTML/SVG 직접 렌더링 금지 (샌드박스 iframe 또는 별도 도메인)

---

## 6. 데이터 보호 (Data Protection)

### 6.1 개인정보 처리

- [ ] 개인정보보호법 준수 (한국)
- [ ] 처리 목적 / 보존 기간 / 제3자 제공 정책 명시
- [ ] 사용자 탈퇴 시 처리 방침 (파일 소유권 이관 vs 삭제)

### 6.2 백업

- [ ] DB: 일일 스냅샷 + PITR (7일)
- [ ] S3: Cross-region replication + 버전 관리
- [ ] 감사 로그: 별도 버킷 + WORM

### 6.3 법적 보존 (Legal Hold)

- [ ] 관리자가 특정 파일/폴더/사용자에 Legal Hold 지정
- [ ] Legal Hold 상태에서는:
  - 삭제 불가 (휴지통 이동도 차단)
  - 영구 삭제 불가 (휴지통 purge 크론도 스킵)
  - 버전 변경 불가
- [ ] 해제는 관리자 2인 승인 (optional)

---

## 7. 비밀번호 / 키 관리

- [ ] .env 파일로 관리, 절대 커밋 금지
- [ ] 운영: AWS Secrets Manager / HashiCorp Vault
- [ ] 키 로테이션 주기

---

## 8. 규정 준수 체크리스트 (도메인별)

### 8.1 공통

- [ ] 개인정보처리방침 제공
- [ ] 이용약관
- [ ] 쿠키 정책

### 8.2 금융 (해당 시)

- [ ] 전자금융감독규정
- [ ] 데이터 국외 이전 금지

### 8.3 의료 (해당 시)

- [ ] 의료법 제21조 (의료정보 보호)
- [ ] HIPAA (해외 환자)

### 8.4 공공 (해당 시)

- [ ] 국가정보보호 지침
- [ ] CSAP 클라우드 보안 인증

---

## 9. 취약점 대응

- [ ] SAST / DAST 도구 도입
- [ ] 의존성 취약점 스캔 (Snyk, Dependabot)
- [ ] 연 1회 외부 모의해킹
- [ ] 취약점 리포트 채널 (security@...)

---

## 10. 인시던트 대응

- [ ] 인시던트 분류 기준 (Severity 1~4)
- [ ] 에스컬레이션 경로
- [ ] 데이터 유출 시 통지 의무 (72시간 내)
- [ ] 사후 검토 (Post-mortem) 템플릿

---

## 작성 우선순위

1. §3 권한 매트릭스 (프론트 `usePermission`과 백엔드 미들웨어 동시 작성)
2. §4 감사 이벤트 정의 (엔드포인트별 어떤 이벤트를 기록할지)
3. §5 저장소 보안 세부
4. §6 Legal Hold (컴플라이언스 요구 시)
5. 나머지는 운영 직전 완성
