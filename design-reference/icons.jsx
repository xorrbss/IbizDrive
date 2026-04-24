// File type icons — minimal geometric marks, no emoji
// Each icon is a React component; we use inline SVG with currentColor.

const ICONS = {
  folder: ({ size = 16, color }) => (
    <svg width={size} height={size} viewBox="0 0 16 16" fill="none">
      <path d="M1.5 4.5C1.5 3.67157 2.17157 3 3 3H6.5L7.8 4.5H13C13.8284 4.5 14.5 5.17157 14.5 6V11.5C14.5 12.3284 13.8284 13 13 13H3C2.17157 13 1.5 12.3284 1.5 11.5V4.5Z" fill={color || "currentColor"} fillOpacity="0.22" stroke={color || "currentColor"} strokeWidth="1"/>
    </svg>
  ),
  doc: ({ size = 16 }) => (
    <svg width={size} height={size} viewBox="0 0 16 16" fill="none">
      <path d="M3 1.5H9L13 5.5V13.5C13 14.3284 12.3284 15 11.5 15H3C2.17157 15 1.5 14.3284 1.5 13.5V3C1.5 2.17157 2.17157 1.5 3 1.5Z" fill="#4A89DC" fillOpacity="0.14" stroke="#4A89DC" strokeWidth="1"/>
      <path d="M9 1.5V5.5H13" stroke="#4A89DC" strokeWidth="1"/>
      <path d="M4.5 8H10M4.5 10H10M4.5 12H8" stroke="#4A89DC" strokeWidth="1" strokeLinecap="round"/>
    </svg>
  ),
  pdf: ({ size = 16 }) => (
    <svg width={size} height={size} viewBox="0 0 16 16" fill="none">
      <path d="M3 1.5H9L13 5.5V13.5C13 14.3284 12.3284 15 11.5 15H3C2.17157 15 1.5 14.3284 1.5 13.5V3C1.5 2.17157 2.17157 1.5 3 1.5Z" fill="#E0564B" fillOpacity="0.14" stroke="#E0564B" strokeWidth="1"/>
      <path d="M9 1.5V5.5H13" stroke="#E0564B" strokeWidth="1"/>
      <text x="3.5" y="12.2" fontSize="4.2" fontWeight="700" fill="#E0564B" fontFamily="ui-monospace,monospace">PDF</text>
    </svg>
  ),
  sheet: ({ size = 16 }) => (
    <svg width={size} height={size} viewBox="0 0 16 16" fill="none">
      <path d="M3 1.5H9L13 5.5V13.5C13 14.3284 12.3284 15 11.5 15H3C2.17157 15 1.5 14.3284 1.5 13.5V3C1.5 2.17157 2.17157 1.5 3 1.5Z" fill="#3EA971" fillOpacity="0.14" stroke="#3EA971" strokeWidth="1"/>
      <path d="M9 1.5V5.5H13" stroke="#3EA971" strokeWidth="1"/>
      <path d="M3.5 8.5H11.5M3.5 10.5H11.5M3.5 12.5H11.5M6 8V13M9 8V13" stroke="#3EA971" strokeWidth="0.8"/>
    </svg>
  ),
  slides: ({ size = 16 }) => (
    <svg width={size} height={size} viewBox="0 0 16 16" fill="none">
      <path d="M3 1.5H9L13 5.5V13.5C13 14.3284 12.3284 15 11.5 15H3C2.17157 15 1.5 14.3284 1.5 13.5V3C1.5 2.17157 2.17157 1.5 3 1.5Z" fill="#E89B3C" fillOpacity="0.14" stroke="#E89B3C" strokeWidth="1"/>
      <path d="M9 1.5V5.5H13" stroke="#E89B3C" strokeWidth="1"/>
      <rect x="3.5" y="8" width="8" height="4" stroke="#E89B3C" strokeWidth="0.9" fill="none"/>
    </svg>
  ),
  image: ({ size = 16 }) => (
    <svg width={size} height={size} viewBox="0 0 16 16" fill="none">
      <rect x="1.5" y="2.5" width="13" height="11" rx="1.5" fill="#B06BCC" fillOpacity="0.14" stroke="#B06BCC" strokeWidth="1"/>
      <circle cx="5" cy="6" r="1" fill="#B06BCC"/>
      <path d="M1.5 11L5 8L8 10.5L11 7.5L14.5 11V12C14.5 12.8 13.8 13.5 13 13.5H3C2.2 13.5 1.5 12.8 1.5 12V11Z" fill="#B06BCC" fillOpacity="0.4"/>
    </svg>
  ),
  video: ({ size = 16 }) => (
    <svg width={size} height={size} viewBox="0 0 16 16" fill="none">
      <rect x="1.5" y="3" width="13" height="10" rx="1.5" fill="#C95A7B" fillOpacity="0.14" stroke="#C95A7B" strokeWidth="1"/>
      <path d="M6.5 6L10 8L6.5 10V6Z" fill="#C95A7B"/>
    </svg>
  ),
  figma: ({ size = 16 }) => (
    <svg width={size} height={size} viewBox="0 0 16 16" fill="none">
      <rect x="2" y="2" width="12" height="12" rx="2" fill="#8B5CF6" fillOpacity="0.14" stroke="#8B5CF6" strokeWidth="1"/>
      <circle cx="8" cy="8" r="2" fill="#8B5CF6" fillOpacity="0.5"/>
      <rect x="5" y="4" width="3" height="2.5" fill="#8B5CF6" fillOpacity="0.5"/>
      <rect x="8" y="4" width="3" height="2.5" fill="#8B5CF6" fillOpacity="0.3"/>
    </svg>
  ),
  code: ({ size = 16 }) => (
    <svg width={size} height={size} viewBox="0 0 16 16" fill="none">
      <path d="M3 1.5H9L13 5.5V13.5C13 14.3284 12.3284 15 11.5 15H3C2.17157 15 1.5 14.3284 1.5 13.5V3C1.5 2.17157 2.17157 1.5 3 1.5Z" fill="#5A7C9E" fillOpacity="0.14" stroke="#5A7C9E" strokeWidth="1"/>
      <path d="M9 1.5V5.5H13" stroke="#5A7C9E" strokeWidth="1"/>
      <path d="M5.5 9L4 10.5L5.5 12M9 9L10.5 10.5L9 12" stroke="#5A7C9E" strokeWidth="1" strokeLinecap="round" strokeLinejoin="round" fill="none"/>
    </svg>
  ),
  archive: ({ size = 16 }) => (
    <svg width={size} height={size} viewBox="0 0 16 16" fill="none">
      <rect x="1.5" y="2.5" width="13" height="4" fill="#8A8270" fillOpacity="0.2" stroke="#8A8270" strokeWidth="1"/>
      <path d="M2.5 6.5H13.5V12.5C13.5 13.3284 12.8284 14 12 14H4C3.17157 14 2.5 13.3284 2.5 12.5V6.5Z" fill="#8A8270" fillOpacity="0.14" stroke="#8A8270" strokeWidth="1"/>
      <rect x="7" y="8.5" width="2" height="2.5" fill="#8A8270" fillOpacity="0.6"/>
    </svg>
  ),
};

function FileIcon({ kind, size = 16 }) {
  const I = ICONS[kind] || ICONS.doc;
  return <I size={size} />;
}

// UI icons (stroke-based, monochrome)
function UIIcon({ name, size = 14 }) {
  const common = { width: size, height: size, viewBox: "0 0 16 16", fill: "none", stroke: "currentColor", strokeWidth: 1.4, strokeLinecap: "round", strokeLinejoin: "round" };
  switch (name) {
    case "chevron-right": return <svg {...common}><path d="M6 4L10 8L6 12"/></svg>;
    case "chevron-down":  return <svg {...common}><path d="M4 6L8 10L12 6"/></svg>;
    case "chevron-left":  return <svg {...common}><path d="M10 4L6 8L10 12"/></svg>;
    case "search":        return <svg {...common}><circle cx="7" cy="7" r="4.5"/><path d="M10.5 10.5L13.5 13.5"/></svg>;
    case "plus":          return <svg {...common}><path d="M8 3V13M3 8H13"/></svg>;
    case "upload":        return <svg {...common}><path d="M8 10V2M5 5L8 2L11 5M3 12V13C3 13.5523 3.44772 14 4 14H12C12.5523 14 13 13.5523 13 13V12"/></svg>;
    case "download":      return <svg {...common}><path d="M8 2V10M5 7L8 10L11 7M3 12V13C3 13.5523 3.44772 14 4 14H12C12.5523 14 13 13.5523 13 13V12"/></svg>;
    case "star":          return <svg {...common}><path d="M8 2L9.8 5.6L13.8 6.2L10.9 9L11.6 13L8 11.1L4.4 13L5.1 9L2.2 6.2L6.2 5.6L8 2Z"/></svg>;
    case "star-fill":     return <svg {...common} fill="currentColor"><path d="M8 2L9.8 5.6L13.8 6.2L10.9 9L11.6 13L8 11.1L4.4 13L5.1 9L2.2 6.2L6.2 5.6L8 2Z"/></svg>;
    case "dots":          return <svg {...common}><circle cx="3.5" cy="8" r="0.8" fill="currentColor"/><circle cx="8" cy="8" r="0.8" fill="currentColor"/><circle cx="12.5" cy="8" r="0.8" fill="currentColor"/></svg>;
    case "share":         return <svg {...common}><circle cx="4" cy="8" r="1.6"/><circle cx="12" cy="4" r="1.6"/><circle cx="12" cy="12" r="1.6"/><path d="M5.4 7.2L10.6 4.8M5.4 8.8L10.6 11.2"/></svg>;
    case "lock":          return <svg {...common}><rect x="3.5" y="7.5" width="9" height="6" rx="1"/><path d="M5.5 7.5V5.5C5.5 4.11929 6.61929 3 8 3C9.38071 3 10.5 4.11929 10.5 5.5V7.5"/></svg>;
    case "trash":         return <svg {...common}><path d="M3 4.5H13M5.5 4.5V3.5C5.5 2.94772 5.94772 2.5 6.5 2.5H9.5C10.0523 2.5 10.5 2.94772 10.5 3.5V4.5M4.5 4.5V13C4.5 13.5523 4.94772 14 5.5 14H10.5C11.0523 14 11.5 13.5523 11.5 13V4.5M7 7V11M9 7V11"/></svg>;
    case "close":         return <svg {...common}><path d="M4 4L12 12M12 4L4 12"/></svg>;
    case "check":         return <svg {...common}><path d="M3 8L6.5 11.5L13 5"/></svg>;
    case "filter":        return <svg {...common}><path d="M2 3H14L10 8V13L6 11V8L2 3Z"/></svg>;
    case "sort":          return <svg {...common}><path d="M4 3V13M4 13L2 11M4 13L6 11M12 13V3M12 3L10 5M12 3L14 5"/></svg>;
    case "list":          return <svg {...common}><path d="M5 4H13M5 8H13M5 12H13M2.5 4V4.01M2.5 8V8.01M2.5 12V12.01" strokeWidth="1.6"/></svg>;
    case "grid":          return <svg {...common}><rect x="2.5" y="2.5" width="4.5" height="4.5"/><rect x="9" y="2.5" width="4.5" height="4.5"/><rect x="2.5" y="9" width="4.5" height="4.5"/><rect x="9" y="9" width="4.5" height="4.5"/></svg>;
    case "info":          return <svg {...common}><circle cx="8" cy="8" r="5.5"/><path d="M8 7.5V11M8 5V5.01"/></svg>;
    case "clock":         return <svg {...common}><circle cx="8" cy="8" r="5.5"/><path d="M8 5V8L10 9.5"/></svg>;
    case "user-plus":     return <svg {...common}><circle cx="6" cy="5.5" r="2.5"/><path d="M2 13C2 10.7909 3.79086 9 6 9C7.5 9 8.8 9.8 9.5 11"/><path d="M11.5 11V14M10 12.5H13"/></svg>;
    case "folder-plus":   return <svg {...common}><path d="M1.5 4.5C1.5 3.67157 2.17157 3 3 3H6.5L7.8 4.5H13C13.8284 4.5 14.5 5.17157 14.5 6V11.5C14.5 12.3284 13.8284 13 13 13H3C2.17157 13 1.5 12.3284 1.5 11.5V4.5Z"/><path d="M8 7.5V10.5M6.5 9H9.5"/></svg>;
    case "home":          return <svg {...common}><path d="M2.5 7L8 2.5L13.5 7V13C13.5 13.5523 13.0523 14 12.5 14H3.5C2.94772 14 2.5 13.5523 2.5 13V7Z"/></svg>;
    case "team":          return <svg {...common}><circle cx="5" cy="6" r="2"/><circle cx="11" cy="6" r="2"/><path d="M2 13C2 11.3431 3.34315 10 5 10C6.65685 10 8 11.3431 8 13M8 13C8 11.3431 9.34315 10 11 10C12.6569 10 14 11.3431 14 13"/></svg>;
    case "menu":          return <svg {...common}><path d="M2.5 4H13.5M2.5 8H13.5M2.5 12H13.5"/></svg>;
    case "file":          return <svg {...common}><path d="M4 2H9L12.5 5.5V13.5C12.5 14 12 14.5 11.5 14.5H4C3.5 14.5 3 14 3 13.5V3C3 2.5 3.5 2 4 2Z"/><path d="M9 2V5.5H12.5"/></svg>;
    case "move":          return <svg {...common}><path d="M8 2V14M2 8H14M5 5L2 8L5 11M11 5L14 8L11 11M5 2L8 5L11 2M5 14L8 11L11 14" strokeWidth="1"/></svg>;
    default: return null;
  }
}

function Avatar({ user, size = 22, stacked = false, idx = 0 }) {
  if (!user) return null;
  const style = {
    width: size, height: size, borderRadius: "50%",
    background: user.color, color: "white",
    display: "inline-flex", alignItems: "center", justifyContent: "center",
    fontSize: size <= 22 ? 9 : 10, fontWeight: 600, letterSpacing: 0,
    fontFamily: "ui-sans-serif,system-ui",
    boxShadow: stacked ? "0 0 0 1.5px var(--bg, white)" : "none",
    marginLeft: stacked && idx > 0 ? -6 : 0,
    flexShrink: 0,
    userSelect: "none",
  };
  return <span style={style} title={user.name}>{user.initials}</span>;
}

function AvatarStack({ userIds, max = 3, size = 22 }) {
  const users = userIds.map(userById).filter(Boolean);
  const shown = users.slice(0, max);
  const extra = users.length - shown.length;
  return (
    <span style={{ display: "inline-flex", alignItems: "center" }}>
      {shown.map((u, i) => <Avatar key={u.id} user={u} size={size} stacked idx={i} />)}
      {extra > 0 && (
        <span style={{
          marginLeft: -6, width: size, height: size, borderRadius: "50%",
          background: "var(--surface-2)", color: "var(--fg-muted)",
          display: "inline-flex", alignItems: "center", justifyContent: "center",
          fontSize: 9, fontWeight: 600,
          boxShadow: "0 0 0 1.5px var(--bg, white)",
        }}>+{extra}</span>
      )}
    </span>
  );
}

Object.assign(window, { FileIcon, UIIcon, Avatar, AvatarStack, ICONS });
