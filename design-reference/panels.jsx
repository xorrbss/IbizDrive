// RightPanel, UploadDock, ConflictDialog, PermissionModal, EmptyStates

const { useState: useState2, useEffect: useEffect2, useMemo: useMemo2 } = React;

// ============================================================================
// RIGHT PANEL (file detail)
// ============================================================================
function RightPanel({ fileId, rows, onClose, onOpenPermissions }) {
  const [tab, setTab] = useState2("detail");
  const file = rows.find((r) => r.id === fileId);
  if (!file) return null;

  const owner = userById(file.owner);
  const modifiedBy = userById(file.modifiedBy);
  const versions = file.id === "fi_02" ? VERSIONS_FI_02 : VERSIONS_FI_02.slice(0, Math.min(file.versions || 1, 4));
  const activity = ACTIVITY_FI_02.slice(0, 6);
  const permissions = PERMISSIONS_FI_02;

  return (
    <aside className="right-panel" data-screen-label="right-panel">
      <div className="rp-head">
        <div className="rp-title-row">
          <FileIcon kind={file.kind} size={18} />
          <span className="rp-title" title={file.name}>{file.name}</span>
          <button className="icon-btn xs" onClick={onClose} title="닫기">
            <UIIcon name="close" size={13} />
          </button>
        </div>
        <div className="rp-actions">
          <button className="btn-ghost btn-xs"><UIIcon name="download" size={12} /><span>다운로드</span></button>
          <button className="btn-ghost btn-xs" onClick={onOpenPermissions}><UIIcon name="user-plus" size={12} /><span>공유</span></button>
          <button className="btn-ghost btn-xs btn-icon-only" title="더보기"><UIIcon name="dots" size={13} /></button>
        </div>
      </div>

      <div className="rp-preview">
        <PreviewCard file={file} />
      </div>

      <div className="rp-tabs">
        {["detail", "version", "activity", "permission"].map((t) => (
          <button
            key={t}
            className={`rp-tab ${tab === t ? "active" : ""}`}
            onClick={() => setTab(t)}
          >
            {{ detail: "상세", version: `버전 ${file.versions || 1}`, activity: "활동", permission: "권한" }[t]}
          </button>
        ))}
      </div>

      <div className="rp-body">
        {tab === "detail" && (
          <div className="detail-list">
            <DetailRow label="종류" value={kindLabel(file.kind)} />
            <DetailRow label="크기" value={formatSize(file.size)} />
            <DetailRow label="소유자" value={owner ? (
              <span className="detail-user"><Avatar user={owner} size={18} /><span>{owner.name}</span></span>
            ) : "—"} />
            <DetailRow label="수정한 사람" value={modifiedBy ? (
              <span className="detail-user"><Avatar user={modifiedBy} size={18} /><span>{modifiedBy.name}</span></span>
            ) : "—"} />
            <DetailRow label="수정일" value={formatDateTime(file.modifiedAt)} />
            <DetailRow label="공유됨" value={file.shared?.length ? (
              <span className="detail-shared">
                <AvatarStack userIds={file.shared} size={18} max={4} />
                <span className="detail-shared-num">{file.shared.length}명</span>
              </span>
            ) : "비공개"} />
            <DetailRow label="경로" value={<span className="detail-path">내 드라이브 / 영업팀 / 계약서</span>} />
            <DetailRow label="위치" value={file.status === "restricted" ? (
              <span className="tag-restricted"><UIIcon name="lock" size={10} /><span>권한 제한</span></span>
            ) : "공개 링크 없음"} />
            <DetailRow label="최근 조회" value="14번" />
          </div>
        )}

        {tab === "version" && (
          <div className="version-list">
            {versions.map((v) => {
              const u = userById(v.uploadedBy);
              return (
                <div key={v.id} className={`version-row ${v.current ? "current" : ""}`}>
                  <div className="version-gutter">
                    <div className="version-dot" />
                    <div className="version-line" />
                  </div>
                  <div className="version-body">
                    <div className="version-head">
                      <span className="version-num">v{v.num}</span>
                      {v.current && <span className="chip-current">현재</span>}
                      <span className="version-size">{formatSize(v.size)}</span>
                    </div>
                    <div className="version-note">{v.note || <em>메모 없음</em>}</div>
                    <div className="version-meta">
                      {u && <Avatar user={u} size={15} />}
                      <span>{u?.name}</span>
                      <span className="dot-sep">·</span>
                      <span>{formatRelative(v.uploadedAt)}</span>
                    </div>
                    {!v.current && (
                      <div className="version-actions">
                        <button className="btn-ghost btn-xs">이 버전으로 복원</button>
                        <button className="btn-ghost btn-xs btn-icon-only"><UIIcon name="download" size={11} /></button>
                      </div>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        )}

        {tab === "activity" && (
          <div className="activity-list">
            {activity.map((a) => {
              const u = userById(a.actor);
              return (
                <div key={a.id} className="activity-row">
                  <Avatar user={u} size={22} />
                  <div className="activity-body">
                    <div className="activity-line">
                      <b>{u?.name}</b>
                      <span>{activityLabel(a.type)}</span>
                    </div>
                    {a.note && <div className="activity-note">{a.note}</div>}
                    <div className="activity-time">{formatRelative(a.at)}</div>
                  </div>
                </div>
              );
            })}
          </div>
        )}

        {tab === "permission" && (
          <div className="perm-list">
            <div className="perm-share-row">
              <button className="btn btn-xs" onClick={onOpenPermissions}>
                <UIIcon name="user-plus" size={12} />
                <span>사용자 추가</span>
              </button>
            </div>
            {permissions.map((p) => {
              const u = userById(p.userId);
              return (
                <div key={p.userId} className="perm-row">
                  <Avatar user={u} size={26} />
                  <div className="perm-body">
                    <div className="perm-name">{u?.name}{p.userId === "u_me" && <span className="chip-you">나</span>}</div>
                    <div className="perm-email">{u?.email}</div>
                    {p.inherited && (
                      <div className="perm-inherited">
                        <UIIcon name="folder-plus" size={9} />
                        <span>'{p.inheritedFrom}'에서 상속</span>
                      </div>
                    )}
                  </div>
                  <select className="perm-role" defaultValue={p.role} disabled={p.userId === "u_me"}>
                    <option value="owner">소유자</option>
                    <option value="editor">편집자</option>
                    <option value="viewer">뷰어</option>
                  </select>
                </div>
              );
            })}
            <div className="perm-link-section">
              <div className="perm-link-head">
                <div>
                  <div className="perm-link-title">링크로 공유</div>
                  <div className="perm-link-sub">링크가 있는 사내 구성원은 뷰어로 접근</div>
                </div>
                <label className="toggle"><input type="checkbox" defaultChecked /><span /></label>
              </div>
              <div className="perm-link-box">
                <span>ibizdrive.com/f/file_xyz789</span>
                <button className="btn-ghost btn-xs">복사</button>
              </div>
            </div>
          </div>
        )}
      </div>
    </aside>
  );
}

function DetailRow({ label, value }) {
  return (
    <div className="detail-row">
      <span className="detail-label">{label}</span>
      <span className="detail-value">{value}</span>
    </div>
  );
}

function PreviewCard({ file }) {
  const map = {
    folder: "var(--accent-soft)", doc: "#E8F0FB", pdf: "#FCE8E4",
    sheet: "#E3F3E9", slides: "#FCEEDA", image: "#F1E6F6",
    video: "#F9E3EA", figma: "#EFE6FA", code: "#E5EAEF", archive: "#EEEBE2",
  };
  return (
    <div className="preview-card" style={{ background: map[file.kind] || "#E8F0FB" }}>
      <FileIcon kind={file.kind} size={52} />
      <div className="preview-lines">
        <div /><div style={{ width: "75%" }} /><div style={{ width: "85%" }} /><div style={{ width: "55%" }} />
      </div>
    </div>
  );
}

function kindLabel(k) {
  return { folder: "폴더", doc: "문서", pdf: "PDF 문서", sheet: "스프레드시트",
    slides: "프레젠테이션", image: "이미지", video: "비디오", figma: "Figma 파일",
    code: "코드", archive: "압축파일" }[k] || k;
}

function activityLabel(type) {
  return {
    "version.created": "새 버전을 업로드했습니다",
    "file.viewed": "파일을 열람했습니다",
    "file.downloaded": "파일을 다운로드했습니다",
    "file.moved": "파일을 이동했습니다",
    "permission.granted": "권한을 변경했습니다",
  }[type] || type;
}

// ============================================================================
// UPLOAD DOCK (sticky, simulated progress)
// ============================================================================
function UploadDock({ tasks, onClear, onRetry, minimized, setMinimized, onResolveConflict }) {
  if (!tasks.length) return null;
  const active = tasks.filter((t) => t.status === "uploading" || t.status === "queued").length;
  const done = tasks.filter((t) => t.status === "done").length;
  const failed = tasks.filter((t) => t.status === "failed").length;
  const conflict = tasks.filter((t) => t.status === "conflict").length;
  const pct = tasks.reduce((s, t) => s + t.progress, 0) / tasks.length;

  return (
    <div className={`upload-dock ${minimized ? "minimized" : ""}`}>
      <div className="upload-dock-head" onClick={() => setMinimized(!minimized)}>
        <div>
          <div className="upload-dock-title">
            {active > 0 ? `${active}개 업로드 중…` : done === tasks.length ? `${done}개 업로드 완료` : "업로드"}
          </div>
          <div className="upload-dock-sub">
            {done}/{tasks.length} 완료
            {failed > 0 && <span className="chip-fail">실패 {failed}</span>}
            {conflict > 0 && <span className="chip-warn">충돌 {conflict}</span>}
          </div>
        </div>
        <div className="upload-dock-actions">
          {active === 0 && <button className="icon-btn xs" onClick={(e) => { e.stopPropagation(); onClear(); }}><UIIcon name="close" size={12} /></button>}
          <UIIcon name={minimized ? "chevron-right" : "chevron-down"} size={13} />
        </div>
      </div>
      {!minimized && (
        <>
          <div className="upload-dock-overall">
            <div className="upload-overall-bar"><div style={{ width: `${pct * 100}%` }} /></div>
          </div>
          <div className="upload-dock-list">
            {tasks.map((t) => (
              <UploadItem key={t.id} task={t} onRetry={onRetry} onResolveConflict={onResolveConflict} />
            ))}
          </div>
        </>
      )}
    </div>
  );
}

function UploadItem({ task, onRetry, onResolveConflict }) {
  return (
    <div className={`upload-item status-${task.status}`}>
      <FileIcon kind={task.kind || "doc"} size={16} />
      <div className="upload-item-body">
        <div className="upload-item-name" title={task.name}>{task.name}</div>
        <div className="upload-item-meta">
          {task.status === "uploading" && <span>{Math.round(task.progress * 100)}% · {formatSize(task.uploadedBytes)} / {formatSize(task.size)}</span>}
          {task.status === "queued" && <span>대기 중</span>}
          {task.status === "done" && <span className="text-ok">완료</span>}
          {task.status === "failed" && <span className="text-fail">{task.error || "실패"}</span>}
          {task.status === "conflict" && <span className="text-warn">동일 이름 파일 존재</span>}
        </div>
        {(task.status === "uploading" || task.status === "queued") && (
          <div className="upload-item-bar"><div style={{ width: `${task.progress * 100}%` }} /></div>
        )}
      </div>
      <div className="upload-item-actions">
        {task.status === "failed" && <button className="btn-ghost btn-xs" onClick={() => onRetry(task.id)}>재시도</button>}
        {task.status === "conflict" && <button className="btn btn-xs" onClick={() => onResolveConflict(task)}>해결</button>}
        {task.status === "done" && <span className="check-ok"><UIIcon name="check" size={12} /></span>}
      </div>
    </div>
  );
}

// ============================================================================
// UPLOAD CONFLICT DIALOG
// ============================================================================
function ConflictDialog({ task, onResolve, onCancel }) {
  const [choice, setChoice] = useState2("new_version");
  const [applyAll, setApplyAll] = useState2(false);
  if (!task) return null;
  return (
    <div className="modal-bg" onClick={onCancel}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <div className="modal-head">
          <h3>동일한 이름의 파일이 존재합니다</h3>
          <button className="icon-btn xs" onClick={onCancel}><UIIcon name="close" size={13} /></button>
        </div>
        <div className="modal-body">
          <div className="conflict-file"><FileIcon kind="doc" size={20} /><span>{task.name}</span></div>
          <p className="modal-sub">현재 폴더에 같은 이름의 파일이 이미 있습니다. 어떻게 처리할까요?</p>
          <div className="radio-list">
            {[
              { v: "new_version", t: "새 버전으로 추가", s: "기존 파일은 유지되고, 버전 히스토리에 이 파일이 추가됩니다." },
              { v: "rename",      t: "이름을 변경하여 업로드", s: `"${task.name.replace(/(\.[^.]+)$/, " (2)$1")}"로 저장됩니다.` },
              { v: "skip",        t: "건너뛰기", s: "이 파일의 업로드를 취소합니다." },
            ].map((o) => (
              <label key={o.v} className={`radio-option ${choice === o.v ? "checked" : ""}`}>
                <input type="radio" checked={choice === o.v} onChange={() => setChoice(o.v)} />
                <div>
                  <div className="radio-title">{o.t}</div>
                  <div className="radio-sub">{o.s}</div>
                </div>
              </label>
            ))}
          </div>
          <label className="checkbox-row">
            <input type="checkbox" checked={applyAll} onChange={(e) => setApplyAll(e.target.checked)} />
            <span>이후 충돌에 동일하게 적용</span>
          </label>
        </div>
        <div className="modal-foot">
          <button className="btn-ghost" onClick={onCancel}>취소</button>
          <button className="btn-primary" onClick={() => onResolve(choice, applyAll)}>적용</button>
        </div>
      </div>
    </div>
  );
}

// ============================================================================
// DROPZONE OVERLAY (OS drag)
// ============================================================================
function DropOverlay({ active, folderName }) {
  if (!active) return null;
  return (
    <div className="drop-overlay">
      <div className="drop-overlay-card">
        <div className="drop-overlay-icon">
          <UIIcon name="upload" size={28} />
        </div>
        <div className="drop-overlay-title">여기에 놓아서 업로드</div>
        <div className="drop-overlay-sub">{folderName}에 파일이 추가됩니다</div>
      </div>
    </div>
  );
}

// ============================================================================
// EMPTY STATES
// ============================================================================
function EmptyFolder({ onUpload }) {
  return (
    <div className="empty-state">
      <div className="empty-icon-bg">
        <FileIcon kind="folder" size={48} />
      </div>
      <h3>이 폴더는 비어있습니다</h3>
      <p>파일을 드래그해서 끌어놓거나 업로드 버튼으로 시작하세요</p>
      <button className="btn-primary" onClick={onUpload}>
        <UIIcon name="upload" size={13} />
        <span>파일 업로드</span>
      </button>
    </div>
  );
}

function ForbiddenState() {
  return (
    <div className="empty-state">
      <div className="empty-icon-bg forbidden">
        <UIIcon name="lock" size={32} />
      </div>
      <h3>접근 권한이 없습니다</h3>
      <p>이 폴더를 보려면 관리자에게 요청하세요.</p>
      <button className="btn-ghost">접근 요청</button>
    </div>
  );
}

Object.assign(window, {
  RightPanel, UploadDock, ConflictDialog, DropOverlay, EmptyFolder, ForbiddenState,
});
