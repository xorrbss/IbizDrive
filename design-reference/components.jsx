// Components: Sidebar, FileTable, RightPanel, Toolbar, UploadDock, etc.

const { useState, useEffect, useRef, useMemo, useCallback } = React;

// ============================================================================
// SIDEBAR
// ============================================================================
function Sidebar({ activeFolderId, onSelectFolder, collapsed, storageUsed = 0.42 }) {
  return (
    <aside className={`sidebar ${collapsed ? "collapsed" : ""}`} data-screen-label="sidebar">
      <div className="sidebar-brand">
        <div className="brand-mark">
          <svg width="20" height="20" viewBox="0 0 20 20" fill="none">
            <rect x="2" y="2" width="16" height="16" rx="4" fill="var(--accent)"/>
            <path d="M6 7.5L10 5L14 7.5V12.5L10 15L6 12.5V7.5Z" fill="white" fillOpacity="0.95"/>
            <path d="M10 5V15" stroke="var(--accent)" strokeWidth="0.8"/>
          </svg>
        </div>
        {!collapsed && <span className="brand-name">IbizDrive</span>}
      </div>

      {!collapsed && (
        <>
          <button className="btn-primary sidebar-new">
            <UIIcon name="plus" size={13} />
            <span>새로 만들기</span>
          </button>

          <nav className="sidebar-nav">
            <SidebarLink icon="home"   label="홈"       active={activeFolderId === "home"} onClick={() => onSelectFolder("home")} />
            <SidebarLink icon="star"   label="즐겨찾기" count={3} />
            <SidebarLink icon="clock"  label="최근" />
            <SidebarLink icon="share"  label="공유됨" />
            <SidebarLink icon="trash"  label="휴지통" />
          </nav>

          <div className="sidebar-section">
            <div className="sidebar-section-head">
              <span>내 드라이브</span>
              <button className="sidebar-section-btn" title="새 폴더"><UIIcon name="plus" size={11} /></button>
            </div>
            <FolderTree
              node={FOLDER_TREE}
              activeId={activeFolderId}
              onSelect={onSelectFolder}
              depth={0}
            />
          </div>

          <div className="sidebar-section">
            <div className="sidebar-section-head">
              <span>공유 드라이브</span>
            </div>
            {SHARED_TREE.map((t) => (
              <div key={t.id} className="folder-row">
                <span className="folder-caret"><UIIcon name="chevron-right" size={10} /></span>
                <UIIcon name="team" size={14} />
                <span className="folder-name">{t.name}</span>
              </div>
            ))}
          </div>

          <div className="sidebar-storage">
            <div className="storage-head">
              <span>저장공간</span>
              <span className="storage-num">{(storageUsed * 100).toFixed(0)}%</span>
            </div>
            <div className="storage-bar"><div style={{ width: `${storageUsed * 100}%` }} /></div>
            <div className="storage-sub">84.3 GB / 200 GB 사용</div>
            <button className="btn-ghost btn-xs" style={{ marginTop: 6, width: "100%" }}>용량 업그레이드</button>
          </div>
        </>
      )}
    </aside>
  );
}

function SidebarLink({ icon, label, count, active, onClick }) {
  return (
    <button className={`sidebar-link ${active ? "active" : ""}`} onClick={onClick}>
      <UIIcon name={icon} size={14} />
      <span className="sidebar-link-label">{label}</span>
      {count != null && <span className="sidebar-link-count">{count}</span>}
    </button>
  );
}

function FolderTree({ node, activeId, onSelect, depth, expandedIds, setExpandedIds }) {
  const [localExpanded, setLocalExpanded] = useState(new Set(["root", "f_sales"]));
  const exp = expandedIds || localExpanded;
  const setExp = setExpandedIds || setLocalExpanded;

  const toggle = (id) => {
    const next = new Set(exp);
    next.has(id) ? next.delete(id) : next.add(id);
    setExp(next);
  };

  const hasChildren = node.children && node.children.length > 0;
  const isExpanded = exp.has(node.id);
  const isActive = activeId === node.id;

  return (
    <>
      <div
        className={`folder-row ${isActive ? "active" : ""}`}
        style={{ paddingLeft: 8 + depth * 12 }}
        onClick={() => onSelect(node.id)}
      >
        <span
          className="folder-caret"
          onClick={(e) => { e.stopPropagation(); if (hasChildren) toggle(node.id); }}
          style={{ opacity: hasChildren ? 1 : 0, cursor: hasChildren ? "default" : "default" }}
        >
          <UIIcon name={isExpanded ? "chevron-down" : "chevron-right"} size={10} />
        </span>
        <FileIcon kind="folder" size={14} />
        <span className="folder-name">{node.name}</span>
      </div>
      {hasChildren && isExpanded && node.children.map((child) => (
        <FolderTree
          key={child.id}
          node={child}
          activeId={activeId}
          onSelect={onSelect}
          depth={depth + 1}
          expandedIds={exp}
          setExpandedIds={setExp}
        />
      ))}
    </>
  );
}

// ============================================================================
// TOPBAR
// ============================================================================
function TopBar({ onSearchFocus, query, setQuery, onToggleSidebar, onToggleTheme, theme }) {
  return (
    <header className="topbar">
      <button className="icon-btn" onClick={onToggleSidebar} title="사이드바">
        <UIIcon name="menu" size={15} />
      </button>
      <div className="search-wrap">
        <UIIcon name="search" size={13} />
        <input
          type="text"
          className="search-input"
          placeholder="드라이브에서 검색…"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          onFocus={onSearchFocus}
        />
        {query && <button className="search-clear" onClick={() => setQuery("")}><UIIcon name="close" size={11} /></button>}
        <span className="search-kbd">⌘K</span>
      </div>
      <div className="topbar-right">
        <button className="icon-btn" onClick={onToggleTheme} title="테마">
          <UIIcon name={theme === "dark" ? "star" : "clock"} size={14} />
        </button>
        <button className="icon-btn" title="도움말"><UIIcon name="info" size={14} /></button>
        <Avatar user={USERS[0]} size={26} />
      </div>
    </header>
  );
}

// ============================================================================
// BREADCRUMB + TOOLBAR
// ============================================================================
function Breadcrumb({ path, onNavigate }) {
  return (
    <nav className="breadcrumb" aria-label="breadcrumb">
      {path.map((p, i) => (
        <span key={p.id} className="breadcrumb-item">
          {i > 0 && <span className="breadcrumb-sep"><UIIcon name="chevron-right" size={10} /></span>}
          <button
            className={`breadcrumb-btn ${i === path.length - 1 ? "last" : ""}`}
            onClick={() => onNavigate(p.id)}
          >
            {p.name}
          </button>
        </span>
      ))}
      <button className="breadcrumb-star" title="즐겨찾기"><UIIcon name="star" size={13} /></button>
    </nav>
  );
}

function Toolbar({ viewMode, setViewMode, sort, setSort, onUpload, onNewFolder, selectionCount }) {
  return (
    <div className="toolbar">
      <div className="toolbar-left">
        <button className="btn" onClick={onUpload}>
          <UIIcon name="upload" size={13} />
          <span>업로드</span>
        </button>
        <button className="btn-ghost" onClick={onNewFolder}>
          <UIIcon name="folder-plus" size={13} />
          <span>새 폴더</span>
        </button>
        <div className="toolbar-divider" />
        <button className="btn-ghost btn-icon-only" title="필터"><UIIcon name="filter" size={13} /></button>
        <div className="sort-chip">
          <span className="sort-chip-label">정렬:</span>
          <select value={sort.key} onChange={(e) => setSort({ ...sort, key: e.target.value })}>
            <option value="name">이름</option>
            <option value="modifiedAt">수정일</option>
            <option value="size">크기</option>
            <option value="owner">소유자</option>
          </select>
          <button
            className="sort-chip-dir"
            onClick={() => setSort({ ...sort, dir: sort.dir === "asc" ? "desc" : "asc" })}
            title={sort.dir === "asc" ? "오름차순" : "내림차순"}
          >
            {sort.dir === "asc" ? "↑" : "↓"}
          </button>
        </div>
      </div>
      <div className="toolbar-right">
        <div className="view-switch">
          <button
            className={viewMode === "list" ? "active" : ""}
            onClick={() => setViewMode("list")}
            title="리스트"
          >
            <UIIcon name="list" size={13} />
          </button>
          <button
            className={viewMode === "grid" ? "active" : ""}
            onClick={() => setViewMode("grid")}
            title="그리드"
          >
            <UIIcon name="grid" size={13} />
          </button>
        </div>
      </div>
    </div>
  );
}

// ============================================================================
// BULK ACTION BAR
// ============================================================================
function BulkActionBar({ count, onClear, onDownload, onMove, onDelete, onShare }) {
  if (count === 0) return null;
  return (
    <div className="bulk-bar">
      <div className="bulk-left">
        <button className="bulk-clear" onClick={onClear} aria-label="선택 해제">
          <UIIcon name="close" size={12} />
        </button>
        <span className="bulk-count">{count}개 선택됨</span>
      </div>
      <div className="bulk-actions">
        <button className="btn-ghost" onClick={onDownload}><UIIcon name="download" size={13} /><span>다운로드</span></button>
        <button className="btn-ghost" onClick={onShare}><UIIcon name="user-plus" size={13} /><span>공유</span></button>
        <button className="btn-ghost" onClick={onMove}><UIIcon name="move" size={13} /><span>이동</span></button>
        <div className="toolbar-divider" />
        <button className="btn-ghost btn-danger" onClick={onDelete}><UIIcon name="trash" size={13} /><span>휴지통으로</span></button>
      </div>
    </div>
  );
}

// ============================================================================
// FILE TABLE
// ============================================================================
function FileTable({
  rows, sort, setSort, selection, setSelection, pending,
  openFileId, setOpenFileId, viewMode, density,
  dragOverId, setDragOverId, draggingIds, setDraggingIds,
}) {
  const sorted = useMemo(() => {
    const arr = [...rows];
    // Folders always on top
    arr.sort((a, b) => {
      if ((a.kind === "folder") !== (b.kind === "folder")) return a.kind === "folder" ? -1 : 1;
      let cmp = 0;
      if (sort.key === "name") cmp = a.name.localeCompare(b.name, "ko");
      else if (sort.key === "modifiedAt") cmp = new Date(a.modifiedAt) - new Date(b.modifiedAt);
      else if (sort.key === "size") cmp = (a.size || 0) - (b.size || 0);
      else if (sort.key === "owner") cmp = (a.owner || "").localeCompare(b.owner || "");
      return sort.dir === "asc" ? cmp : -cmp;
    });
    return arr;
  }, [rows, sort]);

  const orderedIds = sorted.map((r) => r.id);
  const lastClickRef = useRef(null);

  const handleRowClick = (e, id) => {
    if (e.shiftKey && lastClickRef.current) {
      const a = orderedIds.indexOf(lastClickRef.current);
      const b = orderedIds.indexOf(id);
      const [s, en] = a < b ? [a, b] : [b, a];
      const next = new Set(selection);
      orderedIds.slice(s, en + 1).forEach((x) => next.add(x));
      setSelection(next);
    } else if (e.metaKey || e.ctrlKey) {
      const next = new Set(selection);
      next.has(id) ? next.delete(id) : next.add(id);
      setSelection(next);
      lastClickRef.current = id;
    } else {
      setSelection(new Set([id]));
      lastClickRef.current = id;
    }
  };

  const handleRowDblClick = (row) => {
    if (row.kind === "folder") return; // handled by parent onOpen
    setOpenFileId(row.id);
  };

  if (viewMode === "grid") {
    return (
      <GridView
        rows={sorted} selection={selection} setSelection={setSelection}
        onOpen={setOpenFileId} pending={pending}
      />
    );
  }

  const headerCell = (key, label, align = "left", width) => (
    <button
      className={`th ${sort.key === key ? "active" : ""} align-${align}`}
      style={width ? { width } : undefined}
      onClick={() => setSort({ key, dir: sort.key === key && sort.dir === "asc" ? "desc" : "asc" })}
    >
      <span>{label}</span>
      {sort.key === key && <span className="th-arrow">{sort.dir === "asc" ? "↑" : "↓"}</span>}
    </button>
  );

  return (
    <div className={`file-table density-${density}`}>
      <div className="file-table-head">
        <div className="th-check">
          <input
            type="checkbox"
            checked={selection.size > 0 && selection.size === rows.length}
            ref={(el) => { if (el) el.indeterminate = selection.size > 0 && selection.size < rows.length; }}
            onChange={(e) => setSelection(e.target.checked ? new Set(orderedIds) : new Set())}
          />
        </div>
        {headerCell("name", "이름", "left")}
        {headerCell("owner", "소유자", "left", 140)}
        {headerCell("modifiedAt", "수정일", "left", 130)}
        {headerCell("size", "크기", "right", 90)}
        <div className="th th-actions" style={{ width: 44 }} />
      </div>

      <div className="file-table-body">
        {sorted.map((row) => (
          <FileRow
            key={row.id}
            row={row}
            selected={selection.has(row.id)}
            pending={pending.has(row.id)}
            opened={openFileId === row.id}
            onClick={(e) => handleRowClick(e, row.id)}
            onDblClick={() => handleRowDblClick(row)}
            onDragStart={() => setDraggingIds([...selection.has(row.id) ? selection : [row.id]])}
            onDragEnd={() => { setDraggingIds(null); setDragOverId(null); }}
            isDragOverTarget={dragOverId === row.id}
            onDragEnter={() => row.kind === "folder" && setDragOverId(row.id)}
            onDragLeave={() => setDragOverId(null)}
            onDrop={() => setDragOverId(null)}
            onOpenDetail={() => setOpenFileId(row.id)}
          />
        ))}
      </div>
    </div>
  );
}

function FileRow({
  row, selected, pending, opened, onClick, onDblClick,
  onDragStart, onDragEnd, isDragOverTarget, onDragEnter, onDragLeave, onDrop, onOpenDetail,
}) {
  const owner = userById(row.owner);
  return (
    <div
      className={`tr ${selected ? "selected" : ""} ${opened ? "opened" : ""} ${pending ? "pending" : ""} ${isDragOverTarget ? "drop-over" : ""} ${row.status === "restricted" ? "restricted" : ""}`}
      onClick={onClick}
      onDoubleClick={onDblClick}
      draggable
      onDragStart={onDragStart}
      onDragEnd={onDragEnd}
      onDragEnter={(e) => { if (row.kind === "folder") { e.preventDefault(); onDragEnter(); } }}
      onDragOver={(e) => { if (row.kind === "folder") e.preventDefault(); }}
      onDragLeave={onDragLeave}
      onDrop={(e) => { e.preventDefault(); onDrop(); }}
    >
      <div className="td-check">
        <input
          type="checkbox"
          checked={selected}
          onClick={(e) => e.stopPropagation()}
          onChange={onClick}
        />
      </div>
      <div className="td td-name">
        <FileIcon kind={row.kind} size={16} />
        <span className="name-text" title={row.name}>{row.name}</span>
        {row.starred && <span className="badge-star"><UIIcon name="star-fill" size={11} /></span>}
        {row.status === "restricted" && <span className="badge-lock" title="권한 제한"><UIIcon name="lock" size={11} /></span>}
        {row.shared?.length > 1 && (
          <span className="badge-shared"><UIIcon name="share" size={10} /><span>{row.shared.length}</span></span>
        )}
        {row.kind === "folder" && row.items != null && (
          <span className="badge-items">{row.items}개</span>
        )}
      </div>
      <div className="td td-owner">
        {owner && <><Avatar user={owner} size={18} /><span>{owner.id === "u_me" ? "나" : owner.name}</span></>}
      </div>
      <div className="td td-modified">
        <span title={formatDateTime(row.modifiedAt)}>{formatRelative(row.modifiedAt)}</span>
      </div>
      <div className="td td-size">{formatSize(row.size)}</div>
      <div className="td td-actions">
        <button
          className="icon-btn xs"
          onClick={(e) => { e.stopPropagation(); onOpenDetail(); }}
          title="상세"
        >
          <UIIcon name="info" size={13} />
        </button>
        <button className="icon-btn xs" onClick={(e) => e.stopPropagation()} title="더보기">
          <UIIcon name="dots" size={13} />
        </button>
      </div>
    </div>
  );
}

// ============================================================================
// GRID VIEW
// ============================================================================
function GridView({ rows, selection, setSelection, onOpen, pending }) {
  return (
    <div className="grid-view">
      {rows.map((row) => {
        const owner = userById(row.owner);
        const sel = selection.has(row.id);
        return (
          <div
            key={row.id}
            className={`grid-card ${sel ? "selected" : ""} ${pending.has(row.id) ? "pending" : ""}`}
            onClick={(e) => {
              if (e.metaKey || e.ctrlKey) {
                const next = new Set(selection);
                next.has(row.id) ? next.delete(row.id) : next.add(row.id);
                setSelection(next);
              } else {
                setSelection(new Set([row.id]));
              }
            }}
            onDoubleClick={() => row.kind !== "folder" && onOpen(row.id)}
          >
            <div className="grid-thumb">
              <GridThumb kind={row.kind} name={row.name} />
              {row.starred && <span className="grid-star"><UIIcon name="star-fill" size={11} /></span>}
              <input
                type="checkbox"
                className="grid-check"
                checked={sel}
                onClick={(e) => e.stopPropagation()}
                onChange={() => {
                  const next = new Set(selection);
                  next.has(row.id) ? next.delete(row.id) : next.add(row.id);
                  setSelection(next);
                }}
              />
            </div>
            <div className="grid-meta">
              <div className="grid-name" title={row.name}>
                <FileIcon kind={row.kind} size={13} />
                <span>{row.name}</span>
              </div>
              <div className="grid-sub">
                {owner && <Avatar user={owner} size={15} />}
                <span>{formatRelative(row.modifiedAt)}</span>
                <span style={{ marginLeft: "auto" }}>{formatSize(row.size)}</span>
              </div>
            </div>
          </div>
        );
      })}
    </div>
  );
}

function GridThumb({ kind, name }) {
  const paletteMap = {
    folder: "var(--accent-soft)",
    doc: "#E8F0FB",
    pdf: "#FCE8E4",
    sheet: "#E3F3E9",
    slides: "#FCEEDA",
    image: "#F1E6F6",
    video: "#F9E3EA",
    figma: "#EFE6FA",
    code: "#E5EAEF",
    archive: "#EEEBE2",
  };
  const bg = paletteMap[kind] || "#E8F0FB";
  if (kind === "folder") {
    return (
      <div className="thumb-folder" style={{ background: bg }}>
        <FileIcon kind="folder" size={48} />
      </div>
    );
  }
  return (
    <div className="thumb-generic" style={{ background: bg }}>
      <FileIcon kind={kind} size={36} />
      <div className="thumb-lines">
        <div /><div style={{ width: "70%" }} /><div style={{ width: "80%" }} />
      </div>
    </div>
  );
}

Object.assign(window, {
  Sidebar, TopBar, Breadcrumb, Toolbar, BulkActionBar, FileTable, FileRow, GridView,
});
