// Main App — orchestrates state, routing-ish, upload simulation

const { useState: uS, useEffect: uE, useMemo: uM, useRef: uR, useCallback: uC } = React;

function App() {
  const [tweaks, setTweak] = useTweaks(TWEAK_DEFAULTS);

  const [activeFolderId, setActiveFolderId] = uS("root");
  const [selection, setSelection] = uS(new Set());
  const [pending, setPending] = uS(new Set());
  const [openFileId, setOpenFileId] = uS("fi_02");
  const [sort, setSort] = uS({ key: "modifiedAt", dir: "desc" });
  const [viewMode, setViewMode] = uS(tweaks.viewMode || "list");
  const [query, setQuery] = uS("");
  const [sidebarCollapsed, setSidebarCollapsed] = uS(false);
  const [rightPanelOpen, setRightPanelOpen] = uS(tweaks.rightPanelOpen !== false);

  // Upload state
  const [uploads, setUploads] = uS([]);
  const [uploadMinimized, setUploadMinimized] = uS(false);
  const [conflictTask, setConflictTask] = uS(null);
  const [dropActive, setDropActive] = uS(false);

  // DnD
  const [draggingIds, setDraggingIds] = uS(null);
  const [dragOverId, setDragOverId] = uS(null);

  // Theme
  uE(() => {
    document.documentElement.setAttribute("data-theme", tweaks.theme || "light");
    document.documentElement.setAttribute("data-variant", tweaks.variant || "linear");
    document.documentElement.style.setProperty("--accent-override", tweaks.accent || "");
    if (tweaks.accent) {
      document.documentElement.style.setProperty("--accent", tweaks.accent);
    }
  }, [tweaks.theme, tweaks.variant, tweaks.accent]);

  // Global keyboard
  uE(() => {
    const onKey = (e) => {
      if (e.key === "Escape") {
        if (conflictTask) { setConflictTask(null); return; }
        if (openFileId) { setOpenFileId(null); return; }
        setSelection(new Set());
      }
      if ((e.metaKey || e.ctrlKey) && e.key === "a" && !e.target.matches("input,textarea")) {
        e.preventDefault();
        setSelection(new Set(filteredRows.map((r) => r.id)));
      }
      if ((e.metaKey || e.ctrlKey) && e.key === "k") {
        e.preventDefault();
        document.querySelector(".search-input")?.focus();
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [conflictTask, openFileId]);

  // Native OS drop (upload)
  uE(() => {
    let counter = 0;
    const onDragEnter = (e) => {
      if (e.dataTransfer?.types?.includes("Files")) {
        counter++;
        setDropActive(true);
      }
    };
    const onDragLeave = () => { counter--; if (counter <= 0) { counter = 0; setDropActive(false); } };
    const onDragOver = (e) => { if (e.dataTransfer?.types?.includes("Files")) e.preventDefault(); };
    const onDrop = (e) => {
      e.preventDefault();
      counter = 0;
      setDropActive(false);
      if (e.dataTransfer?.files?.length) {
        simulateUpload(Array.from(e.dataTransfer.files));
      }
    };
    window.addEventListener("dragenter", onDragEnter);
    window.addEventListener("dragleave", onDragLeave);
    window.addEventListener("dragover", onDragOver);
    window.addEventListener("drop", onDrop);
    return () => {
      window.removeEventListener("dragenter", onDragEnter);
      window.removeEventListener("dragleave", onDragLeave);
      window.removeEventListener("dragover", onDragOver);
      window.removeEventListener("drop", onDrop);
    };
  }, []);

  const simulateUpload = (files) => {
    const tasks = files.map((f, i) => ({
      id: `up_${Date.now()}_${i}`,
      name: f.name || `업로드-${i + 1}.pdf`,
      size: f.size || Math.round(Math.random() * 5_000_000 + 200_000),
      kind: guessKind(f.name),
      status: "queued",
      progress: 0,
      uploadedBytes: 0,
    }));
    setUploads((prev) => [...tasks, ...prev]);
    setUploadMinimized(false);
    // Kick off fake progress
    tasks.forEach((t, idx) => {
      setTimeout(() => runFakeProgress(t.id), 200 + idx * 400);
    });
  };

  const simulateDemoUpload = () => {
    const demo = [
      { name: "고객 미팅 녹취.m4a", size: 8_412_332 },
      { name: "IbizDrive 로고 시스템.fig", size: 18_224_113 }, // conflict
      { name: "분기보고서 초안.docx", size: 812_003 },
      { name: "제품 데모 영상.mp4", size: 184_222_110 },
    ];
    simulateUpload(demo);
  };

  const runFakeProgress = (taskId) => {
    const tick = () => {
      setUploads((prev) => {
        const next = prev.map((t) => {
          if (t.id !== taskId) return t;
          if (t.status === "done" || t.status === "failed" || t.status === "conflict") return t;
          const inc = 0.03 + Math.random() * 0.12;
          const progress = Math.min(1, t.progress + inc);
          const uploadedBytes = Math.round(t.size * progress);
          // Simulate fi_03 conflict (same name as existing figma)
          if (progress > 0.35 && t.name.includes("로고 시스템") && t.status !== "conflict") {
            return { ...t, status: "conflict", progress };
          }
          // Simulate a random failure (1 in 30)
          if (progress > 0.5 && Math.random() < 0.015 && t.status !== "failed") {
            return { ...t, status: "failed", progress, error: "네트워크 연결 끊김" };
          }
          return {
            ...t,
            progress,
            uploadedBytes,
            status: progress >= 1 ? "done" : "uploading",
          };
        });
        return next;
      });
    };
    const interval = setInterval(() => {
      tick();
      setUploads((prev) => {
        const t = prev.find((x) => x.id === taskId);
        if (!t || t.status === "done" || t.status === "failed" || t.status === "conflict") {
          clearInterval(interval);
        }
        return prev;
      });
    }, 180);
  };

  const retryUpload = (id) => {
    setUploads((prev) => prev.map((t) => t.id === id ? { ...t, status: "queued", error: undefined, progress: 0 } : t));
    setTimeout(() => runFakeProgress(id), 250);
  };

  const resolveConflict = (task) => setConflictTask(task);
  const onConflictResolve = (choice) => {
    const id = conflictTask.id;
    setConflictTask(null);
    if (choice === "skip") {
      setUploads((prev) => prev.filter((t) => t.id !== id));
    } else {
      setUploads((prev) => prev.map((t) => t.id === id ? {
        ...t,
        status: "uploading",
        name: choice === "rename" ? t.name.replace(/(\.[^.]+)$/, " (2)$1") : t.name,
      } : t));
      setTimeout(() => runFakeProgress(id), 200);
    }
  };

  // Breadcrumb path
  const path = uM(() => {
    const stack = [];
    const walk = (node, trail) => {
      const nextTrail = [...trail, { id: node.id, name: node.name }];
      if (node.id === activeFolderId) {
        stack.push(...nextTrail);
        return true;
      }
      if (node.children) {
        for (const c of node.children) if (walk(c, nextTrail)) return true;
      }
      return false;
    };
    walk(FOLDER_TREE, []);
    return stack.length ? stack : [{ id: "root", name: "내 드라이브" }];
  }, [activeFolderId]);

  // Current rows (demo: always ROOT for any folder other than finance which is forbidden)
  const isForbidden = activeFolderId === "f_finance" && !tweaks.showForbidden;
  const baseRows = ROWS_ROOT;
  const filteredRows = uM(() => {
    if (!query.trim()) return baseRows;
    const q = query.toLowerCase();
    return baseRows.filter((r) => r.name.toLowerCase().includes(q));
  }, [query, baseRows]);

  const onDelete = () => {
    const ids = [...selection];
    setPending((p) => new Set([...p, ...ids]));
    setTimeout(() => {
      setPending((p) => { const n = new Set(p); ids.forEach((x) => n.delete(x)); return n; });
      setSelection(new Set());
    }, 1500);
  };

  const mobile = tweaks.platform === "mobile";

  return (
    <div
      className={`app ${mobile ? "mobile-view" : ""}`}
      data-screen-label={mobile ? "mobile" : "desktop"}
    >
      <TopBar
        query={query} setQuery={setQuery}
        onToggleSidebar={() => setSidebarCollapsed(!sidebarCollapsed)}
        onToggleTheme={() => setTweak("theme", tweaks.theme === "dark" ? "light" : "dark")}
        theme={tweaks.theme}
      />

      <div className={`app-main ${sidebarCollapsed ? "no-sidebar" : ""} ${!rightPanelOpen || !openFileId ? "no-rightpanel" : ""}`}>
        {!mobile && (
          <Sidebar
            activeFolderId={activeFolderId}
            onSelectFolder={(id) => { setActiveFolderId(id); setSelection(new Set()); }}
            collapsed={sidebarCollapsed}
          />
        )}

        <div className="app-content">
          <Breadcrumb path={path} onNavigate={setActiveFolderId} />
          <Toolbar
            viewMode={viewMode} setViewMode={setViewMode}
            sort={sort} setSort={setSort}
            onUpload={simulateDemoUpload}
            onNewFolder={() => {}}
            selectionCount={selection.size}
          />
          <BulkActionBar
            count={selection.size}
            onClear={() => setSelection(new Set())}
            onDownload={() => {}}
            onMove={() => {}}
            onDelete={onDelete}
            onShare={() => {}}
          />

          {isForbidden ? (
            <ForbiddenState />
          ) : filteredRows.length === 0 ? (
            query ? (
              <div className="empty-state">
                <div className="empty-icon-bg"><UIIcon name="search" size={32} /></div>
                <h3>'{query}'에 대한 검색 결과 없음</h3>
                <p>파일명, 소유자, 태그로 검색해보세요.</p>
              </div>
            ) : <EmptyFolder onUpload={simulateDemoUpload} />
          ) : (
            <FileTable
              rows={filteredRows}
              sort={sort} setSort={setSort}
              selection={selection} setSelection={setSelection}
              pending={pending}
              openFileId={openFileId}
              setOpenFileId={(id) => { setOpenFileId(id); if (id) setRightPanelOpen(true); }}
              viewMode={viewMode}
              density={tweaks.density}
              dragOverId={dragOverId} setDragOverId={setDragOverId}
              draggingIds={draggingIds} setDraggingIds={setDraggingIds}
            />
          )}

          <div className="status-bar">
            <span>{filteredRows.length}개 항목{selection.size > 0 && ` · ${selection.size}개 선택됨`}</span>
            <span>
              {draggingIds ? `${draggingIds.length}개 드래그 중` : path[path.length - 1]?.name}
            </span>
          </div>

          <DropOverlay active={dropActive} folderName={path[path.length - 1]?.name || "내 드라이브"} />
        </div>

        {!mobile && rightPanelOpen && openFileId && (
          <RightPanel
            fileId={openFileId}
            rows={baseRows}
            onClose={() => setOpenFileId(null)}
            onOpenPermissions={() => {}}
          />
        )}
      </div>

      <UploadDock
        tasks={uploads}
        onClear={() => setUploads([])}
        onRetry={retryUpload}
        minimized={uploadMinimized}
        setMinimized={setUploadMinimized}
        onResolveConflict={resolveConflict}
      />

      {conflictTask && (
        <ConflictDialog
          task={conflictTask}
          onResolve={onConflictResolve}
          onCancel={() => setConflictTask(null)}
        />
      )}

      <TweaksPanel>
        <TweakSection label="프리셋 스타일" />
        <TweakRadio
          label="Variant" value={tweaks.variant}
          options={["linear", "notion", "dropbox", "terminal"]}
          onChange={(v) => setTweak("variant", v)}
        />
        <TweakSection label="테마" />
        <TweakRadio
          label="모드" value={tweaks.theme}
          options={["light", "dark"]}
          onChange={(v) => setTweak("theme", v)}
        />
        <TweakColor
          label="Accent"
          value={tweaks.accent}
          onChange={(v) => setTweak("accent", v)}
        />
        <TweakSection label="레이아웃" />
        <TweakRadio
          label="밀도" value={tweaks.density}
          options={["compact", "comfortable"]}
          onChange={(v) => setTweak("density", v)}
        />
        <TweakRadio
          label="뷰" value={viewMode}
          options={["list", "grid"]}
          onChange={(v) => { setViewMode(v); setTweak("viewMode", v); }}
        />
        <TweakToggle
          label="우측 패널"
          value={rightPanelOpen}
          onChange={(v) => { setRightPanelOpen(v); setTweak("rightPanelOpen", v); }}
        />
        <TweakRadio
          label="플랫폼" value={tweaks.platform}
          options={["desktop", "mobile"]}
          onChange={(v) => setTweak("platform", v)}
        />
        <TweakSection label="상태 시뮬레이션" />
        <TweakButton label="업로드 시작" onClick={simulateDemoUpload} />
        <TweakToggle
          label="재무 폴더 접근 허용"
          value={!!tweaks.showForbidden}
          onChange={(v) => setTweak("showForbidden", v)}
        />
      </TweaksPanel>
    </div>
  );
}

function guessKind(name = "") {
  const ext = name.toLowerCase().split(".").pop();
  if (["doc", "docx", "md", "txt"].includes(ext)) return "doc";
  if (ext === "pdf") return "pdf";
  if (["xls", "xlsx", "csv"].includes(ext)) return "sheet";
  if (["ppt", "pptx", "key"].includes(ext)) return "slides";
  if (["png", "jpg", "jpeg", "gif", "webp"].includes(ext)) return "image";
  if (["mp4", "mov", "avi", "m4a", "mp3"].includes(ext)) return "video";
  if (ext === "fig") return "figma";
  if (["js", "ts", "py", "yaml", "json"].includes(ext)) return "code";
  if (["zip", "rar", "tar", "gz"].includes(ext)) return "archive";
  return "doc";
}

const TWEAK_DEFAULTS = /*EDITMODE-BEGIN*/{
  "variant": "linear",
  "theme": "light",
  "accent": "",
  "density": "comfortable",
  "viewMode": "list",
  "rightPanelOpen": true,
  "platform": "desktop",
  "showForbidden": false
}/*EDITMODE-END*/;

const root = ReactDOM.createRoot(document.getElementById("root"));
root.render(<App />);
