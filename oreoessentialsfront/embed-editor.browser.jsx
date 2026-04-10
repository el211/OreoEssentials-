const DISCORD_DARK = "#313338";
const DISCORD_DARKER = "#2b2d31";
const DISCORD_DARKEST = "#1e1f22";
const DISCORD_BLURPLE = "#5865F2";
const DISCORD_GREEN = "#57F287";
const DISCORD_TEXT = "#dbdee1";
const DISCORD_MUTED = "#949ba4";
const DISCORD_INPUT = "#383a40";
const DISCORD_BORDER = "#1e1f22";

function EmbedEditor() {
  const [text, setText] = React.useState("");
  const [title, setTitle] = React.useState("");
  const [copied, setCopied] = React.useState(false);
  const [copiedTitle, setCopiedTitle] = React.useState(false);

  const convertedDesc = text.replace(/\n/g, "\\n");
  const convertedTitle = title.replace(/\n/g, "\\n");

  const copyDesc = React.useCallback(() => {
    navigator.clipboard.writeText(convertedDesc).then(() => {
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    });
  }, [convertedDesc]);

  const copyTitle = React.useCallback(() => {
    navigator.clipboard.writeText(convertedTitle).then(() => {
      setCopiedTitle(true);
      setTimeout(() => setCopiedTitle(false), 2000);
    });
  }, [convertedTitle]);

  const lineCount = text ? text.split("\n").length : 0;
  const charCount = text.length;

  const styles = {
    app: {
      minHeight: "100vh",
      background: DISCORD_DARKEST,
      display: "flex",
      alignItems: "center",
      justifyContent: "center",
      fontFamily: "'gg sans', 'Noto Sans', Whitney, sans-serif",
      padding: "24px",
      boxSizing: "border-box",
    },
    card: {
      background: DISCORD_DARKER,
      borderRadius: "8px",
      width: "100%",
      maxWidth: "720px",
      overflow: "hidden",
      boxShadow: "0 8px 32px rgba(0,0,0,0.5)",
    },
    header: {
      background: DISCORD_DARK,
      padding: "16px 20px",
      borderBottom: `1px solid ${DISCORD_BORDER}`,
      display: "flex",
      alignItems: "center",
      gap: "10px",
    },
    headerIcon: {
      width: "32px",
      height: "32px",
      background: DISCORD_BLURPLE,
      borderRadius: "50%",
      display: "flex",
      alignItems: "center",
      justifyContent: "center",
      fontSize: "16px",
      flexShrink: 0,
    },
    headerTitle: {
      color: DISCORD_TEXT,
      fontWeight: 700,
      fontSize: "16px",
      margin: 0,
    },
    headerSub: {
      color: DISCORD_MUTED,
      fontSize: "12px",
      margin: "2px 0 0 0",
    },
    body: {
      padding: "20px",
      display: "flex",
      flexDirection: "column",
      gap: "20px",
    },
    section: {
      display: "flex",
      flexDirection: "column",
      gap: "8px",
    },
    label: {
      color: DISCORD_MUTED,
      fontSize: "11px",
      fontWeight: 700,
      letterSpacing: "0.5px",
      textTransform: "uppercase",
    },
    textareaWrap: {
      position: "relative",
    },
    textarea: {
      width: "100%",
      minHeight: "180px",
      background: DISCORD_INPUT,
      border: `1px solid ${DISCORD_BORDER}`,
      borderRadius: "4px",
      color: DISCORD_TEXT,
      fontSize: "14px",
      lineHeight: "1.6",
      padding: "12px",
      resize: "vertical",
      outline: "none",
      boxSizing: "border-box",
      fontFamily: "'gg sans', 'Noto Sans', Whitney, sans-serif",
      transition: "border-color 0.15s",
    },
    titleInput: {
      width: "100%",
      background: DISCORD_INPUT,
      border: `1px solid ${DISCORD_BORDER}`,
      borderRadius: "4px",
      color: DISCORD_TEXT,
      fontSize: "14px",
      lineHeight: "1.6",
      padding: "10px 12px",
      outline: "none",
      boxSizing: "border-box",
      fontFamily: "'gg sans', 'Noto Sans', Whitney, sans-serif",
      transition: "border-color 0.15s",
    },
    stats: {
      display: "flex",
      gap: "12px",
      color: DISCORD_MUTED,
      fontSize: "11px",
    },
    outputBox: {
      background: DISCORD_DARKEST,
      border: `1px solid ${DISCORD_BORDER}`,
      borderRadius: "4px",
      padding: "12px",
      color: "#a3b3c2",
      fontSize: "13px",
      fontFamily: "'Consolas', 'Courier New', monospace",
      wordBreak: "break-all",
      lineHeight: "1.6",
      minHeight: "48px",
      maxHeight: "120px",
      overflowY: "auto",
    },
    outputEmpty: {
      color: DISCORD_MUTED,
      fontStyle: "italic",
    },
    copyBtn: {
      alignSelf: "flex-end",
      background: DISCORD_BLURPLE,
      color: "white",
      border: "none",
      borderRadius: "4px",
      padding: "8px 20px",
      fontSize: "13px",
      fontWeight: 600,
      cursor: "pointer",
      transition: "background 0.15s, transform 0.1s",
      fontFamily: "'gg sans', 'Noto Sans', Whitney, sans-serif",
      letterSpacing: "0.2px",
    },
    copyBtnSuccess: {
      background: "#3ba55d",
    },
    divider: {
      height: "1px",
      background: DISCORD_BORDER,
      margin: "0 -20px",
    },
    previewBlock: {
      background: DISCORD_DARK,
      borderLeft: `4px solid ${DISCORD_BLURPLE}`,
      borderRadius: "0 4px 4px 0",
      padding: "12px 16px",
    },
    previewTitle: {
      color: DISCORD_TEXT,
      fontWeight: 700,
      fontSize: "15px",
      marginBottom: "4px",
    },
    previewDesc: {
      color: DISCORD_TEXT,
      fontSize: "14px",
      lineHeight: "1.6",
      whiteSpace: "pre-wrap",
    },
    previewEmpty: {
      color: DISCORD_MUTED,
      fontStyle: "italic",
      fontSize: "13px",
    },
    tip: {
      background: "rgba(88, 101, 242, 0.1)",
      border: `1px solid rgba(88, 101, 242, 0.3)`,
      borderRadius: "4px",
      padding: "10px 14px",
      color: "#8ea1e1",
      fontSize: "12px",
      lineHeight: "1.5",
    },
  };

  return (
    <div style={styles.app}>
      <div style={styles.card}>
        {/* Header */}
        <div style={styles.header}>
          <div style={styles.headerIcon}>📝</div>
          <div>
            <p style={styles.headerTitle}>/embed Editor</p>
            <p style={styles.headerSub}>Write normally → copies with \\n for Discord</p>
          </div>
        </div>

        <div style={styles.body}>
          {/* Tip */}
          <div style={styles.tip}>
            💡 Write your text with <strong>Enter</strong> for line breaks. The output below automatically converts them to <code>{"\\n"}</code> for Discord's <code>/embed description</code> field.
          </div>

          {/* Title field */}
          <div style={styles.section}>
            <label style={styles.label}>Title (optional)</label>
            <input
              style={styles.titleInput}
              placeholder="Ex: REGLE"
              value={title}
              onChange={e => setTitle(e.target.value)}
              onFocus={e => e.target.style.borderColor = DISCORD_BLURPLE}
              onBlur={e => e.target.style.borderColor = DISCORD_BORDER}
            />
            {title && (
              <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
                <div style={{ ...styles.outputBox, flex: 1, maxHeight: "none", minHeight: "auto", padding: "8px 12px" }}>
                  {convertedTitle || <span style={styles.outputEmpty}>—</span>}
                </div>
                <button
                  style={{
                    ...styles.copyBtn,
                    ...(copiedTitle ? styles.copyBtnSuccess : {}),
                    whiteSpace: "nowrap",
                    padding: "8px 14px",
                  }}
                  onClick={copyTitle}
                  onMouseEnter={e => !copiedTitle && (e.target.style.background = "#4752c4")}
                  onMouseLeave={e => !copiedTitle && (e.target.style.background = DISCORD_BLURPLE)}
                >
                  {copiedTitle ? "✓ Copied!" : "Copy"}
                </button>
              </div>
            )}
          </div>

          <div style={styles.divider} />

          {/* Description textarea */}
          <div style={styles.section}>
            <label style={styles.label}>Description</label>
            <div style={styles.textareaWrap}>
              <textarea
                style={styles.textarea}
                placeholder={"💬 1. Respect avant tout\n🔇 2. Pas de spam\n🎮 3. Règles pendant les lives\n..."}
                value={text}
                onChange={e => setText(e.target.value)}
                onFocus={e => e.target.style.borderColor = DISCORD_BLURPLE}
                onBlur={e => e.target.style.borderColor = DISCORD_BORDER}
              />
            </div>
            <div style={styles.stats}>
              <span>{charCount} characters</span>
              <span>{lineCount} line{lineCount !== 1 ? "s" : ""}</span>
              {charCount > 2048 && <span style={{ color: "#ed4245" }}>⚠ Discord embeds max 4096 chars</span>}
            </div>
          </div>

          {/* Output */}
          <div style={styles.section}>
            <label style={styles.label}>Output for Discord (paste in /embed description)</label>
            <div style={styles.outputBox}>
              {convertedDesc
                ? convertedDesc
                : <span style={styles.outputEmpty}>Start typing above…</span>
              }
            </div>
            <button
              style={{
                ...styles.copyBtn,
                ...(copied ? styles.copyBtnSuccess : {}),
              }}
              onClick={copyDesc}
              disabled={!text}
              onMouseEnter={e => !copied && text && (e.target.style.background = "#4752c4")}
              onMouseLeave={e => !copied && (e.target.style.background = DISCORD_BLURPLE)}
            >
              {copied ? "✓ Copied!" : "📋 Copy for Discord"}
            </button>
          </div>

          <div style={styles.divider} />

          {/* Live preview */}
          <div style={styles.section}>
            <label style={styles.label}>Live Preview (how it'll look in Discord)</label>
            <div style={styles.previewBlock}>
              {title && <div style={styles.previewTitle}>{title}</div>}
              {text
                ? <div style={styles.previewDesc}>{text}</div>
                : <div style={styles.previewEmpty}>Preview will appear here…</div>
              }
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}


// Mount
const root = ReactDOM.createRoot(document.getElementById('embed-editor-root'));
root.render(<EmbedEditor />);
