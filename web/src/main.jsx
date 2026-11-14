import React, { useEffect, useMemo, useState } from "react";
import { createRoot } from "react-dom/client";
import {
  MessageSquare,
  Smartphone,
  Search,
  ArrowUpRight,
  ShieldCheck,
  Settings,
  Copy,
  LockKeyhole,
  X,
  Inbox,
  LogOut,
  Trash2,
  Check,
  Link2,
} from "lucide-react";
import {
  onAuthStateChanged,
  signInWithEmailAndPassword,
  signOut,
} from "firebase/auth";
import {
  collection,
  onSnapshot,
  query,
  orderBy,
  limit,
  doc,
  updateDoc,
  deleteDoc,
} from "firebase/firestore";
import { auth, authReady, db, configured } from "./firebase";
import {
  generateKey,
  importKey,
  decryptMessage,
  extractOtp,
} from "./crypto.mjs";
import "./style.css";

const now = Date.now();
const demoDevices = [
  {
    id: "nord",
    name: "OnePlus Nord 3",
    enabled: true,
    lastSeen: { toMillis: () => now - 60000 },
  },
  {
    id: "pad",
    name: "POCO Pad",
    enabled: true,
    lastSeen: { toMillis: () => now - 180000 },
  },
];
const demoMessages = [
  {
    id: "a",
    deviceId: "pad",
    sender: "Example Bank",
    body: "Your verification code is 482913. This sample code is for the LinkSMS demo only. Never share a real code with anyone.",
    sim: "SIM 1 · Personal",
    receivedAt: now - 60000,
  },
  {
    id: "b",
    deviceId: "nord",
    sender: "Alex",
    body: "Made it to the airport. Let me know when you land — see you soon!",
    sim: "SIM 1 · Personal",
    receivedAt: now - 420000,
  },
  {
    id: "c",
    deviceId: "nord",
    sender: "Example Account",
    body: "Your sign-in code is 709821. This is a sample message, not a real verification code.",
    sim: "SIM 2 · Work",
    receivedAt: now - 1200000,
  },
  {
    id: "d",
    deviceId: "pad",
    sender: "Mobile Network",
    body: "Your plan has been renewed. Enjoy your calls and data. Have a great day!",
    sim: "SIM 1 · Personal",
    receivedAt: now - 3600000,
  },
  {
    id: "e",
    deviceId: "nord",
    sender: "Sam",
    body: "Sending you the details in a bit. Have a safe trip!",
    sim: "SIM 2 · Work",
    receivedAt: now - 7200000,
  },
];
function time(value) {
  return new Date(value).toLocaleTimeString([], {
    hour: "2-digit",
    minute: "2-digit",
  });
}
function App() {
  const [user, setUser] = useState(null),
    [authLoading, setAuthLoading] = useState(configured);
  const [demo, setDemo] = useState(
      !configured || new URLSearchParams(location.search).get("demo") === "1",
    ),
    [devices, setDevices] = useState([]),
    [messages, setMessages] = useState([]);
  const [records, setRecords] = useState([]),
    [key, setKey] = useState(null),
    [keyText, setKeyText] = useState("");
  const [selected, setSelected] = useState("a"),
    [filter, setFilter] = useState("all"),
    [search, setSearch] = useState("");
  const [settings, setSettings] = useState(false),
    [error, setError] = useState(""),
    [notice, setNotice] = useState("");
  const [email, setEmail] = useState(""),
    [password, setPassword] = useState(""),
    [busy, setBusy] = useState(false);
  const [loaded, setLoaded] = useState(false),
    [otpOnly, setOtpOnly] = useState(false);
  useEffect(() => {
    if (!auth) return;
    return onAuthStateChanged(auth, (u) => {
      setUser(u);
      setAuthLoading(false);
      setKey(null);
      setKeyText("");
      setMessages([]);
      setRecords([]);
      setDevices([]);
      setLoaded(false);
    });
  }, []);
  useEffect(() => {
    if (!user || demo) return;
    const fail = (e) => {
      setError(
        "Cannot load your inbox. Check the connection and Firestore rules.",
      );
      setLoaded(true);
    };
    const stopDevices = onSnapshot(
      collection(db, "users", user.uid, "devices"),
      (s) => setDevices(s.docs.map((d) => ({ id: d.id, ...d.data() }))),
      fail,
    );
    const stopMessages = onSnapshot(
      query(
        collection(db, "users", user.uid, "messages"),
        orderBy("receivedAt", "desc"),
        limit(200),
      ),
      (s) => {
        setRecords(s.docs.map((d) => ({ id: d.id, ...d.data() })));
        setLoaded(true);
      },
      fail,
    );
    return () => {
      stopDevices();
      stopMessages();
    };
  }, [user, demo]);
  useEffect(() => {
    let active = true;
    if (!key || !user) {
      setMessages([]);
      return;
    }
    Promise.all(
      records.map(async (r) => {
        try {
          return { ...r, ...(await decryptMessage(key, r, user.uid)) };
        } catch {
          return {
            ...r,
            sender: "Unable to decrypt",
            body: "This message uses a different key or its encrypted data is invalid.",
            sim: "Unknown SIM",
            locked: true,
          };
        }
      }),
    ).then((m) => {
      if (active) setMessages(m);
    });
    return () => {
      active = false;
    };
  }, [records, key, user]);
  useEffect(() => {
    if (!notice) return;
    const timer = setTimeout(() => setNotice(""), 3500);
    return () => clearTimeout(timer);
  }, [notice]);
  const allDevices = demo ? demoDevices : devices;
  const allMessages = demo ? demoMessages : messages;
  const visible = useMemo(
    () =>
      allMessages.filter(
        (m) =>
          (filter === "all" || m.deviceId === filter) &&
          (!otpOnly || extractOtp(m.body)) &&
          `${m.sender} ${m.body} ${m.sim}`
            .toLowerCase()
            .includes(search.toLowerCase()),
      ),
    [allMessages, filter, search, otpOnly],
  );
  const current = visible.find((m) => m.id === selected) ?? visible[0];
  const deviceName = (id) =>
    allDevices.find((d) => d.id === id)?.name ?? "Unknown device";
  const copy = async (text) => {
    try {
      await navigator.clipboard.writeText(text);
      setNotice("Copied to clipboard");
    } catch {
      setError(
        "Clipboard access was blocked. Select and copy the text manually.",
      );
    }
  };
  async function unlock() {
    try {
      setKey(await importKey(keyText));
      setKeyText("");
      setNotice("Inbox unlocked for this session");
      setSettings(false);
    } catch (e) {
      setError(e.message);
    }
  }
  async function login(e) {
    e.preventDefault();
    setBusy(true);
    setError("");
    try {
      await authReady;
      await signInWithEmailAndPassword(auth, email.trim(), password);
      setPassword("");
      setDemo(false);
    } catch {
      setError("Sign-in failed. Check your email, password and connection.");
    } finally {
      setBusy(false);
    }
  }
  async function pauseDevice(d) {
    try {
      await updateDoc(doc(db, "users", user.uid, "devices", d.id), {
        enabled: !d.enabled,
      });
      setNotice(d.enabled ? "Device uploads paused" : "Device uploads enabled");
    } catch {
      setError("Could not update this device.");
    }
  }
  return (
    <div className="shell">
      <aside className="sidebar">
        <a className="brand" href="/" aria-label="LinkSMS home">
          <span className="brand-icon">
            <Link2 size={23} />
          </span>
          LinkSMS<span className="brand-dot">.</span>
        </a>
        <div className="workspace">
          <span className="avatar">S</span>
          <div>
            <strong>Personal space</strong>
            <small>
              {demo
                ? "Demo workspace"
                : user
                  ? "Private workspace"
                  : "Welcome back"}
            </small>
          </div>
          <ShieldCheck size={16} />
        </div>
        <p className="eyebrow">WORKSPACE</p>
        <button
          className={`nav ${filter === "all" && !otpOnly ? "active" : ""}`}
          onClick={() => {
            setFilter("all");
            setOtpOnly(false);
          }}
        >
          <Inbox size={18} />
          All messages<span className="count">{allMessages.length}</span>
        </button>
        <button
          className={`nav ${otpOnly ? "active" : ""}`}
          onClick={() => {
            setFilter("all");
            setOtpOnly(!otpOnly);
          }}
        >
          <LockKeyhole size={18} />
          Verification codes
        </button>
        <div className="section-label">
          <p className="eyebrow">YOUR DEVICES</p>
          <span>{allDevices.length.toString().padStart(2, "0")}</span>
        </div>
        {allDevices.map((d) => (
          <button
            key={d.id}
            className={`device-nav ${filter === d.id ? "chosen" : ""}`}
            onClick={() => {
              setFilter(d.id);
              setOtpOnly(false);
            }}
          >
            <Smartphone size={19} />
            <span>
              <strong>{d.name}</strong>
              <small>{d.enabled ? "Sync enabled" : "Uploads paused"}</small>
            </span>
            <i className={d.enabled ? "dot" : "dot paused"} />
          </button>
        ))}
        {!allDevices.length && (
          <p className="muted small">Connected phones will appear here.</p>
        )}
        <button className="add-device" onClick={() => setSettings(true)}>
          + Connect a device
        </button>
        <div className="sidebar-bottom">
          <div className="privacy-note">
            <ShieldCheck size={21} />
            <strong>A little peace of mind.</strong>
            <p>Your messages, available wherever you are.</p>
          </div>
          <button className="nav" onClick={() => setSettings(true)}>
            <Settings size={18} />
            Settings & setup
          </button>
          {user && !demo && (
            <button
              className="nav"
              onClick={async () => {
                await signOut(auth);
                setKey(null);
              }}
            >
              <LogOut size={18} />
              Sign out
            </button>
          )}
        </div>
      </aside>
      <main>
        <header>
          <span className="breadcrumb">
            Workspace <span>/</span> Messages
          </span>
          <span className="status-pill">
            <span className="dot" />
            {demo ? "Sample data" : user ? "Signed in" : "Private inbox"}
          </span>
        </header>
        <section className="page-heading">
          <div>
            <p className="eyebrow green">A LITTLE CLOSER, WHEREVER YOU ARE</p>
            <h1>Your messages, together.</h1>
            <p>Two devices. Three SIMs. One less thing to worry about.</p>
          </div>
          <button className="secondary" onClick={() => setSettings(true)}>
            <Smartphone size={16} />
            Manage devices
            <ArrowUpRight size={15} />
          </button>
        </section>
        {error && (
          <div role="alert" className="alert">
            {error}
            <button aria-label="Dismiss error" onClick={() => setError("")}>
              <X size={16} />
            </button>
          </div>
        )}
        {demo && (
          <div className="demo-banner">
            <span>
              <strong>Take a look around.</strong> This inbox contains sample
              messages.
            </span>
            <button
              onClick={() => {
                if (configured) {
                  setDemo(false);
                } else {
                  setSettings(true);
                }
              }}
            >
              {configured ? "Connect your inbox" : "Set up live sync"}
              <ArrowUpRight size={15} />
            </button>
          </div>
        )}
        {!demo && !user ? (
          <section className="login card">
            <LockKeyhole size={32} />
            <h2>Your private inbox awaits.</h2>
            <p>Use the same account on your Android devices and here.</p>
            <form onSubmit={login}>
              <label>
                Email
                <input
                  type="email"
                  required
                  autoComplete="username"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                />
              </label>
              <label>
                Password
                <input
                  type="password"
                  required
                  autoComplete="current-password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                />
              </label>
              <button className="primary" disabled={busy || authLoading}>
                {busy ? "Signing in…" : "Sign in"}
              </button>
            </form>
            <button className="text-button" onClick={() => setDemo(true)}>
              Explore the demo
            </button>
          </section>
        ) : (
          <>
            <div className="overview">
              <div>
                <span className="stat-icon">
                  <Smartphone size={21} />
                </span>
                <span>
                  <small>Connected devices</small>
                  <strong>
                    {allDevices.length}
                    <em> / 2 planned</em>
                  </strong>
                </span>
              </div>
              <div>
                <span className="stat-icon">
                  <MessageSquare size={21} />
                </span>
                <span>
                  <small>{demo ? "Sample messages" : "Recent messages"}</small>
                  <strong>
                    {demo ? allMessages.length : records.length}
                    <em> in your inbox</em>
                  </strong>
                </span>
              </div>
              <div>
                <span className="stat-icon">
                  <ShieldCheck size={21} />
                </span>
                <span>
                  <small>Message protection</small>
                  <strong className="text-stat">
                    {demo
                      ? "Encrypted by design"
                      : key
                        ? "Unlocked this session"
                        : "Inbox locked"}
                  </strong>
                </span>
              </div>
            </div>
            <section className="inbox card">
              <div className="inbox-toolbar">
                <h2>
                  {otpOnly
                    ? "Verification codes"
                    : filter === "all"
                      ? "All messages"
                      : deviceName(filter)}
                  <span>{visible.length}</span>
                </h2>
                <label className="search">
                  <Search size={17} />
                  <input
                    aria-label="Search messages"
                    placeholder="Search messages…"
                    value={search}
                    onChange={(e) => setSearch(e.target.value)}
                  />
                </label>
              </div>
              {!demo && !key ? (
                <div className="empty">
                  <LockKeyhole size={36} />
                  <h2>Only you can open this inbox.</h2>
                  <p>
                    Enter your encryption key to decrypt messages in this
                    browser.
                  </p>
                  <button className="primary" onClick={() => setSettings(true)}>
                    Unlock inbox
                  </button>
                </div>
              ) : !visible.length ? (
                <div className="empty">
                  <Inbox size={36} />
                  <h2>
                    {!demo && !loaded
                      ? "Loading your inbox…"
                      : "No messages here yet."}
                  </h2>
                  <p>
                    {search || otpOnly
                      ? "Try another search or filter."
                      : "Enable sync on an Android device, then send it a new SMS."}
                  </p>
                </div>
              ) : (
                <div className="inbox-columns">
                  <div className="message-list">
                    {visible.map((m) => (
                      <button
                        key={m.id}
                        className={`message-row ${current?.id === m.id ? "selected" : ""}`}
                        onClick={() => setSelected(m.id)}
                      >
                        <span
                          className={`sender-avatar ${extractOtp(m.body) ? "code-avatar" : ""}`}
                        >
                          {extractOtp(m.body) ? (
                            <LockKeyhole size={19} />
                          ) : (
                            m.sender[0]
                          )}
                        </span>
                        <span className="message-summary">
                          <span className="message-top">
                            <strong>{m.sender}</strong>
                            <time>{time(m.receivedAt)}</time>
                          </span>
                          <span className="preview">{m.body}</span>
                          <span className="message-tags">
                            <span>{deviceName(m.deviceId)}</span>
                            <span>{m.sim}</span>
                          </span>
                        </span>
                      </button>
                    ))}
                  </div>
                  {current && (
                    <article className="message-detail">
                      <div className="detail-title">
                        <span className="sender-avatar large">
                          {current.sender[0]}
                        </span>
                        <div>
                          <h2>{current.sender}</h2>
                          <p>
                            {current.sim} <span>·</span>{" "}
                            {deviceName(current.deviceId)}
                          </p>
                        </div>
                      </div>
                      <div className="date-divider">
                        {new Date(current.receivedAt).toLocaleDateString([], {
                          weekday: "long",
                          month: "short",
                          day: "numeric",
                        })}{" "}
                        · {time(current.receivedAt)}
                      </div>
                      <div className="message-bubble">{current.body}</div>
                      {extractOtp(current.body) && (
                        <div className="otp-card">
                          <div>
                            <small>VERIFICATION CODE</small>
                            <strong>{extractOtp(current.body)}</strong>
                          </div>
                          <button
                            className="secondary"
                            onClick={() => copy(extractOtp(current.body))}
                          >
                            <Copy size={15} />
                            Copy code
                          </button>
                        </div>
                      )}
                      <div className="detail-actions">
                        <button
                          className="text-button"
                          onClick={() => copy(current.body)}
                        >
                          <Copy size={14} />
                          Copy message
                        </button>
                        {!demo && (
                          <button
                            className="text-button danger"
                            onClick={async () => {
                              if (
                                !confirm(
                                  "Delete this encrypted message from your synced inbox? The original SMS stays on your phone.",
                                )
                              )
                                return;
                              try {
                                await deleteDoc(
                                  doc(
                                    db,
                                    "users",
                                    user.uid,
                                    "messages",
                                    current.id,
                                  ),
                                );
                              } catch {
                                setError("Could not delete this message.");
                              }
                            }}
                          >
                            <Trash2 size={14} />
                            Delete
                          </button>
                        )}
                      </div>
                      <div className="encrypted-footnote">
                        <ShieldCheck size={14} />
                        {demo
                          ? "Sample message · no real account data"
                          : "Decrypted locally in your browser"}
                      </div>
                    </article>
                  )}
                </div>
              )}
            </section>
            <footer>
              <span>
                <LockKeyhole size={13} />
                Your private corner of the internet.
              </span>
              <span>
                {demo
                  ? "LinkSMS · Preview"
                  : "Latest 200 messages · New incoming SMS only"}
              </span>
            </footer>
          </>
        )}
      </main>
      {settings && (
        <div className="modal-overlay" onClick={() => setSettings(false)}>
          <section
            className="modal"
            role="dialog"
            aria-modal="true"
            aria-labelledby="settings-title"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="modal-heading">
              <h2 id="settings-title">Connect your world.</h2>
              <button
                aria-label="Close settings"
                onClick={() => setSettings(false)}
              >
                <X />
              </button>
            </div>
            <p className="muted">
              Project <code>linksms-9e899</code> · Private receive-only preview
            </p>
            {!configured && (
              <div className="setup-note">
                Live sync needs your Firebase web configuration in{" "}
                <code>web/.env.local</code> and{" "}
                <code>google-services.json</code> in the Android app. Follow the
                project README, then restart the web app.
              </div>
            )}
            <h3>1. Your encryption key</h3>
            <p>
              Generate one key and save it in your password manager. Paste the
              same key into both Android apps. It stays in browser memory only;
              refresh or sign out to lock the inbox.
            </p>
            <label>
              Encryption key
              <input
                value={keyText}
                onChange={(e) => setKeyText(e.target.value)}
                type="password"
                autoComplete="off"
                placeholder="Paste your saved key"
              />
            </label>
            <div className="button-row">
              <button
                className="secondary"
                onClick={() => setKeyText(generateKey())}
              >
                Generate key
              </button>
              <button
                className="secondary"
                disabled={!keyText}
                onClick={() => copy(keyText)}
              >
                <Copy size={15} />
                Copy key
              </button>
              <button className="primary" disabled={!keyText} onClick={unlock}>
                Unlock inbox
              </button>
            </div>
            <p className="small muted">
              Keep your existing key. Generating a new one will not unlock
              messages encrypted with the old one. The app cannot recover a lost
              key.
            </p>
            <h3>2. Connect your Android devices</h3>
            <p>
              Install LinkSMS on your Nord 3 and POCO Pad. Sign in with the same
              account, paste your key, allow SMS access, and enable sync.
            </p>
            {allDevices.map((d) => (
              <div className="connected-device" key={d.id}>
                <Smartphone size={22} />
                <div>
                  <strong>{d.name}</strong>
                  <small>
                    Last contact:{" "}
                    {d.lastSeen?.toMillis
                      ? new Date(d.lastSeen.toMillis()).toLocaleString()
                      : "Waiting for sync"}
                  </small>
                </div>
                {!demo && (
                  <button className="secondary" onClick={() => pauseDevice(d)}>
                    {d.enabled ? "Pause" : "Resume"}
                  </button>
                )}
              </div>
            ))}
            <p className="small muted">
              Pausing blocks uploads until resumed. This is not account
              revocation. Both devices share your account; remove lost-device
              access through your account and rotate the key.
            </p>
          </section>
        </div>
      )}
      {notice && (
        <div role="status" className="toast">
          <Check size={17} />
          {notice}
        </div>
      )}
    </div>
  );
}
createRoot(document.getElementById("root")).render(<App />);
