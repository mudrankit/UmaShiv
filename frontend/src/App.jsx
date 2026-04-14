import { useEffect, useState } from "react";

const examplePrompts = [
  "Design Uber",
  "Design WhatsApp"
];

const modelCatalog = [
  {
    provider: "demo",
    label: "Demo mode",
    description: "No API key needed",
    models: [
      { value: "demo-template", label: "Demo template" }
    ]
  },
  {
    provider: "gemini",
    label: "Google Gemini",
    description: "Uses the Gemini API",
    models: [
      { value: "gemini-2.5-flash", label: "Gemini 2.5 Flash" },
      { value: "gemini-2.5-flash-lite", label: "Gemini 2.5 Flash-Lite" }
    ]
  },
  {
    provider: "groq",
    label: "Groq",
    description: "OpenAI-compatible endpoint",
    models: [
      { value: "llama-3.1-8b-instant", label: "Llama 3.1 8B Instant" },
      { value: "llama3-8b-8192", label: "Llama 3 8B 8192" },
      { value: "llama3-70b-8192", label: "Llama 3 70B 8192" }
    ]
  },
  {
    provider: "openai",
    label: "OpenAI",
    description: "Temporarily unavailable due to quota limits",
    disabled: true,
    models: [
      { value: "gpt-4o-mini", label: "GPT-4o Mini" },
      { value: "gpt-4o", label: "GPT-4o" }
    ]
  }
];

const sectionOrder = [
  { id: "functional", title: "Functional requirements" },
  { id: "nonFunctional", title: "Non-functional requirements" },
  { id: "entities", title: "Entities" },
  { id: "apis", title: "APIs" },
  { id: "diagram", title: "HLD diagram" },
  { id: "summary", title: "Summary" },
  { id: "tradeOffs", title: "Trade-offs" },
  { id: "followUps", title: "Follow-up questions" }
];

const defaultProvider = modelCatalog.find((item) => item.provider === "gemini") || modelCatalog[0];
const defaultModel = defaultProvider.models[0].value;
const emptyRating = { score: 0, verdict: "", strengths: [], improvements: [] };
const apiBaseUrl = import.meta.env.VITE_API_BASE_URL || "";
const googleClientId = import.meta.env.VITE_GOOGLE_CLIENT_ID || "";
const authStorageKey = "system-design-auth-token";
const responseHistoryStorageKey = "system-design-response-history";

function loadCachedResponses() {
  try {
    const raw = localStorage.getItem(responseHistoryStorageKey);
    const parsed = raw ? JSON.parse(raw) : [];
    return Array.isArray(parsed) ? parsed : [];
  } catch (error) {
    return [];
  }
}

function saveCachedResponses(items) {
  localStorage.setItem(responseHistoryStorageKey, JSON.stringify(items.slice(0, 10)));
}

async function readErrorMessage(response, fallbackMessage) {
  try {
    const payload = await response.json();
    return payload.message || fallbackMessage;
  } catch (error) {
    return fallbackMessage;
  }
}

function buildCacheKey(prompt, provider, model, interviewerMode) {
  return [provider, model, interviewerMode ? "interviewer" : "standard", prompt.trim().toLowerCase()].join("::");
}

function App() {
  const [authToken, setAuthToken] = useState(localStorage.getItem(authStorageKey) || "");
  const [currentUser, setCurrentUser] = useState(null);
  const [authMode, setAuthMode] = useState("login");
  const [authLoading, setAuthLoading] = useState(Boolean(localStorage.getItem(authStorageKey)));
  const [authSubmitting, setAuthSubmitting] = useState(false);
  const [authAction, setAuthAction] = useState("");
  const [logoutSubmitting, setLogoutSubmitting] = useState(false);
  const [authError, setAuthError] = useState("");
  const [supportOpen, setSupportOpen] = useState(false);
  const [supportSubmitting, setSupportSubmitting] = useState(false);
  const [supportError, setSupportError] = useState("");
  const [supportSuccess, setSupportSuccess] = useState("");
  const [supportForm, setSupportForm] = useState({ donorName: "", amount: "" });
  const [showPassword, setShowPassword] = useState(false);
  const [authForm, setAuthForm] = useState({
    email: "",
    username: "",
    identifier: "",
    password: ""
  });
  const [prompt, setPrompt] = useState("Design Uber");
  const [selectedProvider, setSelectedProvider] = useState(defaultProvider.provider);
  const [selectedModel, setSelectedModel] = useState(defaultModel);
  const [interviewerMode, setInterviewerMode] = useState(true);
  const [cachedResponses, setCachedResponses] = useState(() => loadCachedResponses());
  const [design, setDesign] = useState(() => {
    const cached = loadCachedResponses();
    return cached.length > 0 ? cached[0].design : null;
  });
  const [activeSection, setActiveSection] = useState("functional");
  const [rating, setRating] = useState(4);
  const [ratingResult, setRatingResult] = useState(emptyRating);
  const [loadingDesign, setLoadingDesign] = useState(false);
  const [loadingRating, setLoadingRating] = useState(false);
  const [loadingMessage, setLoadingMessage] = useState("");
  const [error, setError] = useState("");

  const activeProvider = modelCatalog.find((item) => item.provider === selectedProvider) || defaultProvider;

  useEffect(() => {
    if (apiBaseUrl) {
      fetch(`${apiBaseUrl}/api/health`).catch(() => undefined);
    }

    if (!authToken) {
      setAuthLoading(false);
      return;
    }

    restoreSession(authToken);
  }, []);

  useEffect(() => {
    if (currentUser || !googleClientId || !window.google) {
      return undefined;
    }

    window.google.accounts.id.initialize({
      client_id: googleClientId,
      callback: async (response) => {
        await loginWithGoogle(response.credential);
      }
    });

    const container = document.getElementById("google-signin-button");
    if (container) {
      container.innerHTML = "";
      window.google.accounts.id.renderButton(container, {
        theme: "outline",
        size: "large",
        width: 320,
        text: authMode === "signup" ? "signup_with" : "signin_with"
      });
    }

    return undefined;
  }, [currentUser, authMode]);

  async function restoreSession(token) {
    setAuthLoading(true);
    try {
      const response = await fetch(`${apiBaseUrl}/api/auth/session`, {
        headers: {
          Authorization: `Bearer ${token}`
        }
      });

      if (!response.ok) {
        throw new Error(await readErrorMessage(response, "Please log in to continue."));
      }

      const user = await response.json();
      setCurrentUser(user);
      setAuthToken(token);
      setAuthError("");
    } catch (sessionError) {
      localStorage.removeItem(authStorageKey);
      setAuthToken("");
      setCurrentUser(null);
      setAuthError("");
    } finally {
      setAuthLoading(false);
    }
  }

  async function handleAuthSubmit(event) {
    event.preventDefault();
    setAuthSubmitting(true);
    setAuthAction(authMode === "signup" ? "Creating your account" : "Signing you in");
    setAuthError("");

    try {
      const endpoint = authMode === "signup" ? "/api/auth/register" : "/api/auth/login";
      const payload = authMode === "signup"
        ? {
            email: authForm.email,
            username: authForm.username,
            password: authForm.password
          }
        : {
            identifier: authForm.identifier,
            password: authForm.password
          };

      const response = await fetch(`${apiBaseUrl}${endpoint}`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json"
        },
        body: JSON.stringify(payload)
      });

      if (!response.ok) {
        throw new Error(await readErrorMessage(response, "Unable to authenticate right now."));
      }

      const data = await response.json();
      finalizeAuth(data);
    } catch (requestError) {
      setAuthError(requestError.message);
    } finally {
      setAuthSubmitting(false);
      setAuthAction("");
    }
  }

  async function loginWithGoogle(idToken) {
    setAuthSubmitting(true);
    setAuthAction("Checking your Google account");
    setAuthError("");

    try {
      const response = await fetch(`${apiBaseUrl}/api/auth/google`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json"
        },
        body: JSON.stringify({ idToken })
      });

      if (!response.ok) {
        throw new Error(await readErrorMessage(response, "Google sign-in failed."));
      }

      const data = await response.json();
      finalizeAuth(data);
    } catch (requestError) {
      setAuthError(requestError.message);
    } finally {
      setAuthSubmitting(false);
      setAuthAction("");
    }
  }

  function finalizeAuth(data) {
    setCurrentUser(data.user);
    setAuthToken(data.token);
    localStorage.setItem(authStorageKey, data.token);
    setAuthForm({ email: "", username: "", identifier: "", password: "" });
    setAuthError("");
  }

  async function logout() {
    setLogoutSubmitting(true);
    setError("");

    try {
      if (authToken) {
        await fetch(`${apiBaseUrl}/api/auth/logout`, {
          method: "POST",
          headers: {
            Authorization: `Bearer ${authToken}`
          }
        });
      }
    } catch (requestError) {
      // ignore logout errors and clear local session anyway
    } finally {
      localStorage.removeItem(authStorageKey);
      setAuthToken("");
      setCurrentUser(null);
      setDesign(null);
      setRatingResult(emptyRating);
      setError("");
      setLogoutSubmitting(false);
    }
  }

  const authorizedFetch = async (url, options = {}) => {
    const headers = {
      ...(options.headers || {}),
      Authorization: `Bearer ${authToken}`
    };
    return fetch(url, { ...options, headers });
  };

  const generateDesign = async (event) => {
    event.preventDefault();
    const normalizedPrompt = prompt.trim();
    const cacheKey = buildCacheKey(normalizedPrompt, selectedProvider, selectedModel, interviewerMode);
    const cachedMatch = cachedResponses.find((item) => item.key === cacheKey);

    setError("");
    setRatingResult(emptyRating);

    if (cachedMatch) {
      setLoadingMessage("Using a cached response for this prompt");
      setLoadingDesign(true);
      setDesign(null);
      window.setTimeout(() => {
        setDesign(cachedMatch.design);
        setActiveSection("functional");
        setLoadingDesign(false);
        setLoadingMessage("");
      }, 250);
      return;
    }

    setLoadingDesign(true);
    setLoadingMessage(`Generating a fresh design for "${normalizedPrompt}"`);
    setDesign(null);

    try {
      const response = await authorizedFetch(`${apiBaseUrl}/api/design/generate`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json"
        },
        body: JSON.stringify({
          prompt: normalizedPrompt,
          interviewerMode,
          provider: selectedProvider,
          model: selectedModel
        })
      });

      if (!response.ok) {
        throw new Error(await readErrorMessage(response, "The assistant could not generate a design right now."));
      }

      const data = await response.json();
      const nextCachedResponses = [
        {
          key: cacheKey,
          prompt: normalizedPrompt,
          provider: selectedProvider,
          model: selectedModel,
          interviewerMode,
          createdAt: new Date().toISOString(),
          design: data
        },
        ...cachedResponses.filter((item) => item.key !== cacheKey)
      ].slice(0, 10);
      setCachedResponses(nextCachedResponses);
      saveCachedResponses(nextCachedResponses);
      setDesign(data);
      setActiveSection("functional");
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setLoadingDesign(false);
      setLoadingMessage("");
    }
  };

  const submitRating = async (value) => {
    if (!design) {
      return;
    }

    setRating(value);
    setLoadingRating(true);

    try {
      const response = await authorizedFetch(`${apiBaseUrl}/api/design/rate`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json"
        },
        body: JSON.stringify({
          prompt,
          designMarkdown: design.rawMarkdown,
          rating: value
        })
      });

      if (!response.ok) {
        throw new Error(await readErrorMessage(response, "Unable to score this design right now."));
      }

      const data = await response.json();
      setRatingResult(data);
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setLoadingRating(false);
    }
  };

  async function startSupportPayment(event) {
    event.preventDefault();
    setSupportSubmitting(true);
    setSupportError("");
    setSupportSuccess("");

    try {
      if (!window.Razorpay) {
        throw new Error("Payment checkout is not available right now. Please refresh and try again.");
      }

      const amount = Number(supportForm.amount);
      const donorName = supportForm.donorName.trim();
      if (!donorName) {
        throw new Error("Please enter the donor name.");
      }
      if (!Number.isFinite(amount) || amount <= 0) {
        throw new Error("Please enter a valid amount.");
      }

      const orderResponse = await fetch(`${apiBaseUrl}/api/payments/donations/order`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json"
        },
        body: JSON.stringify({ donorName, amount })
      });

      if (!orderResponse.ok) {
        throw new Error(await readErrorMessage(orderResponse, "Unable to start the payment flow right now."));
      }

      const order = await orderResponse.json();
      const razorpay = new window.Razorpay({
        key: order.keyId,
        amount: order.amount,
        currency: order.currency,
        name: order.businessName,
        description: order.description,
        order_id: order.orderId,
        handler: async (paymentResult) => {
          try {
            const verifyResponse = await fetch(`${apiBaseUrl}/api/payments/donations/verify`, {
              method: "POST",
              headers: {
                "Content-Type": "application/json"
              },
              body: JSON.stringify({
                donationId: order.donationId,
                razorpayOrderId: paymentResult.razorpay_order_id,
                razorpayPaymentId: paymentResult.razorpay_payment_id,
                razorpaySignature: paymentResult.razorpay_signature
              })
            });

            if (!verifyResponse.ok) {
              throw new Error(await readErrorMessage(verifyResponse, "Payment verification failed."));
            }

            const verified = await verifyResponse.json();
            setSupportSuccess(verified.message || "Payment received. Thank you for your support.");
            setSupportForm({ donorName: "", amount: "" });
            setSupportSubmitting(false);
          } catch (verificationError) {
            setSupportError(verificationError.message);
            setSupportSubmitting(false);
          }
        },
        prefill: {
          name: donorName
        },
        theme: {
          color: "#ff6b35"
        },
        modal: {
          ondismiss: () => {
            setSupportSubmitting(false);
          }
        }
      });
      razorpay.on("payment.failed", (failure) => {
        const reason = failure && failure.error && failure.error.description
          ? failure.error.description
          : "Payment was not completed.";
        setSupportError(reason);
        setSupportSubmitting(false);
      });
      razorpay.open();
    } catch (paymentError) {
      setSupportError(paymentError.message);
      setSupportSubmitting(false);
    }
  }

  function closeSupportModal() {
    if (supportSubmitting) {
      return;
    }
    setSupportOpen(false);
    setSupportError("");
    setSupportSuccess("");
  }

  function resetAuthForm(nextMode) {
    setAuthMode(nextMode);
    setAuthForm({ email: "", username: "", identifier: "", password: "" });
    setShowPassword(false);
    setAuthError("");
  }

  function clearDisplayedOutput() {
    setDesign(null);
    setRatingResult(emptyRating);
    setError("");
  }

  const handleProviderChange = (event) => {
    const nextProvider = modelCatalog.find((item) => item.provider === event.target.value) || defaultProvider;
    if (nextProvider.disabled) {
      return;
    }
    setSelectedProvider(nextProvider.provider);
    setSelectedModel(nextProvider.models[0].value);
  };

  if (authLoading) {
    return <div className="auth-loading">Restoring your session...</div>;
  }

  if (!currentUser) {
    return (
      <>
        <div className="auth-shell">
          <div className="auth-card">
            <div className="auth-hero">
              <h1 className="welcome-title">Welcome to Umashiv</h1>
              <p className="hero-copy">
                Create an account with email, username, and password, or use Google sign-in if it is configured.
              </p>
              <button type="button" className="primary-button auth-submit support-button" onClick={() => setSupportOpen(true)}>
                Please support us
              </button>
            </div>

            <div className="auth-tabs">
              <button
                type="button"
                className={authMode === "login" ? "auth-tab active" : "auth-tab"}
                onClick={() => resetAuthForm("login")}
                disabled={authSubmitting}
              >
                Log in
              </button>
              <button
                type="button"
                className={authMode === "signup" ? "auth-tab active" : "auth-tab"}
                onClick={() => resetAuthForm("signup")}
                disabled={authSubmitting}
              >
                Register
              </button>
            </div>

            <form className="auth-form" onSubmit={handleAuthSubmit}>
              {authMode === "signup" ? (
                <>
                  <label className="label" htmlFor="email">Email</label>
                  <input
                    id="email"
                    type="email"
                    value={authForm.email}
                    onChange={(event) => setAuthForm({ ...authForm, email: event.target.value })}
                    required
                    disabled={authSubmitting}
                  />

                  <label className="label" htmlFor="username">Username</label>
                  <input
                    id="username"
                    type="text"
                    value={authForm.username}
                    onChange={(event) => setAuthForm({ ...authForm, username: event.target.value })}
                    required
                    disabled={authSubmitting}
                  />
                </>
              ) : (
                <>
                  <label className="label" htmlFor="identifier">Email or username</label>
                  <input
                    id="identifier"
                    type="text"
                    value={authForm.identifier}
                    onChange={(event) => setAuthForm({ ...authForm, identifier: event.target.value })}
                    required
                    disabled={authSubmitting}
                  />
                </>
              )}

              <label className="label" htmlFor="password">Password</label>
              <div className="password-row">
                <input
                  id="password"
                  type={showPassword ? "text" : "password"}
                  value={authForm.password}
                  onChange={(event) => setAuthForm({ ...authForm, password: event.target.value })}
                  required
                  disabled={authSubmitting}
                />
                <button
                  type="button"
                  className="password-toggle"
                  onClick={() => setShowPassword((value) => !value)}
                  disabled={authSubmitting}
                  aria-label={showPassword ? "Password visible" : "Password hidden"}
                  title={showPassword ? "Password visible" : "Password hidden"}
                >
                  {showPassword ? <EyeIcon /> : <EyeOffIcon />}
                </button>
              </div>

              <button className="primary-button auth-submit" type="submit" disabled={authSubmitting}>
                {authSubmitting ? `${authAction}...` : authMode === "signup" ? "Create account" : "Log in"}
              </button>
            </form>

            <div className="auth-divider"><span>or</span></div>
            {googleClientId ? (
              <div id="google-signin-button" className="google-slot" />
            ) : (
              <button type="button" className="google-fallback" disabled>
                Google sign-in available after setting VITE_GOOGLE_CLIENT_ID and APP_AUTH_GOOGLE_CLIENT_ID
              </button>
            )}

            {authSubmitting ? <BusyNotice title={authAction} subtitle="This can take a little longer when the backend is waking up." /> : null}
            {authError ? <p className="error-banner">{authError}</p> : null}
          </div>
        </div>
        {supportOpen ? renderSupportModal() : null}
      </>
    );
  }

  const sectionData = design
    ? {
        functional: design.functionalRequirements,
        nonFunctional: design.nonFunctionalRequirements,
        entities: design.entities,
        apis: design.apis,
        diagram: design.diagram,
        summary: design.summary,
        tradeOffs: design.tradeOffs,
        followUps: design.followUpQuestions
      }
    : null;

  return (
    <div className="app-shell">
      <div className="page-glow page-glow-left" />
      <div className="page-glow page-glow-right" />

      <main className="workspace">
        <section className="control-panel">
          <div className="app-topbar">
            <div>
              <p className="eyebrow">Welcome back</p>
              <strong className="user-name">{currentUser.username}</strong>
            </div>
            <button type="button" className="ghost-button logout-button" onClick={logout} disabled={logoutSubmitting}>
              {logoutSubmitting ? "Logging out..." : "Log out"}
            </button>
          </div>

          {logoutSubmitting ? <BusyNotice title="Ending your session" subtitle="We are securely clearing your saved login from this device." compact /> : null}

          <div className="hero-block">
            <p className="eyebrow">AI-powered interview prep</p>
            <p className="hero-copy">
              Generate interview-ready system design answers
            </p>
          </div>

          <form className="composer" onSubmit={generateDesign}>
            <div className="composer-grid">
              <div className="field-group">
                <label className="label" htmlFor="provider">AI provider</label>
                <select id="provider" value={selectedProvider} onChange={handleProviderChange} disabled={loadingDesign || logoutSubmitting}>
                  {modelCatalog.map((option) => (
                    <option key={option.provider} value={option.provider} disabled={Boolean(option.disabled)}>{option.label}</option>
                  ))}
                </select>
              </div>

              <div className="field-group">
                <label className="label" htmlFor="model">Model</label>
                <select id="model" value={selectedModel} onChange={(event) => setSelectedModel(event.target.value)} disabled={loadingDesign || logoutSubmitting}>
                  {activeProvider.models.map((option) => (
                    <option key={option.value} value={option.value}>{option.label}</option>
                  ))}
                </select>
              </div>
            </div>

            <div className="prompt-examples-row">
              {examplePrompts.map((item) => (
                <button key={item} type="button" className="chip prompt-chip" onClick={() => setPrompt(item)} disabled={loadingDesign || logoutSubmitting}>
                  {item}
                </button>
              ))}
            </div>

            <label className="label" htmlFor="prompt">Interview prompt</label>
            <textarea id="prompt" value={prompt} onChange={(event) => setPrompt(event.target.value)} placeholder="Design Uber" rows={5} disabled={loadingDesign || logoutSubmitting} />

            <div className="composer-footer">
              <label className="toggle">
                <input type="checkbox" checked={interviewerMode} onChange={(event) => setInterviewerMode(event.target.checked)} disabled={loadingDesign || logoutSubmitting} />
                <span>Interviewer-grade trade-offs and questions</span>
              </label>
              <button className="primary-button" type="submit" disabled={loadingDesign || logoutSubmitting}>
                {loadingDesign ? "Generating..." : "Generate design"}
              </button>
            </div>
          </form>

          {loadingDesign ? <BusyNotice title="Generating your design" subtitle="We are clearing the old answer and preparing a fresh response." compact /> : null}

          <div className="status-strip">
            <span className="status-pill">{selectedProvider} / {selectedModel}</span>
            <span className="status-pill">Signed in as {currentUser.email}</span>
          </div>

          {error ? <p className="error-banner">{error}</p> : null}
        </section>

        <section className="results-shell">
          <div className="results-topbar">
            <div>
              <p className="eyebrow">Assistant output</p>
              <h2>{design ? design.title : loadingDesign ? "Preparing a new interview answer" : "Your interview answer will appear here"}</h2>
            </div>
            <div className="results-actions">
              <span className="provider-badge">
                {selectedProvider + " / " + selectedModel}
              </span>
              <button
                type="button"
                className="ghost-button clear-output-button"
                onClick={clearDisplayedOutput}
                disabled={!design && !error && !ratingResult.verdict && !loadingDesign}
              >
                Clear output
              </button>
            </div>
          </div>

          {loadingDesign ? (
            <div className="loading-state">
              <BusyNotice title={loadingMessage || "Generating your design"} subtitle="The previous output is hidden so you know a new answer is on the way." />
              <div className="loading-skeleton-grid">
                <div className="loading-skeleton-card" />
                <div className="loading-skeleton-card" />
                <div className="loading-skeleton-card wide" />
              </div>
            </div>
          ) : design ? (
            <div className="results-workspace">
              <aside className="sections-rail">
                {sectionOrder.map((section) => (
                  <button
                    key={section.id}
                    type="button"
                    className={activeSection === section.id ? "section-tab active" : "section-tab"}
                    onClick={() => setActiveSection(section.id)}
                  >
                    <span>{section.title}</span>
                  </button>
                ))}
              </aside>

              <div className="content-stage">
                <div className="overview-grid">
                  <MetricCard label="Functional items" value={design.functionalRequirements.length} />
                  <MetricCard label="Entities" value={design.entities.length} />
                  <MetricCard label="API surfaces" value={design.apis.length} />
                  <MetricCard label="Follow-ups" value={design.followUpQuestions.length} />
                </div>

                <SectionPane activeSection={activeSection} sectionData={sectionData} />

                <section className="rating-card">
                  <div className="rating-top">
                    <div>
                      <p className="eyebrow">Rate this design</p>
                      <h3>How interview-ready does it feel?</h3>
                    </div>
                    <div className="star-row" aria-label="rating control">
                      {[1, 2, 3, 4, 5].map((value) => (
                        <button key={value} type="button" className={value <= rating ? "star active" : "star"} onClick={() => submitRating(value)}>
                          {"\u2605"}
                        </button>
                      ))}
                    </div>
                  </div>

                  {loadingRating ? (
                    <p className="muted-copy">Scoring the design...</p>
                  ) : ratingResult.verdict ? (
                    <div className="rating-feedback">
                      <p className="score-pill">{ratingResult.score}/100</p>
                      <p className="verdict">{ratingResult.verdict}</p>
                      <div className="feedback-columns">
                        <FeedbackList title="Strengths" items={ratingResult.strengths} />
                        <FeedbackList title="Improve next" items={ratingResult.improvements} />
                      </div>
                    </div>
                  ) : (
                    <p className="muted-copy">Rate the answer to get interview-readiness feedback after reviewing the sections.</p>
                  )}
                </section>
              </div>
            </div>
          ) : (
            <div className="empty-state">
              <p>Are you ready to begin with the interview?</p>
            </div>
          )}
        </section>
      </main>
    </div>
  );

  function renderSupportModal() {
    return (
      <div className="support-overlay" role="dialog" aria-modal="true">
        <div className="support-card">
          <div className="support-header">
            <div>
              <p className="eyebrow">Support Umashiv</p>
              <h3>Continue with your donation</h3>
            </div>
            <button type="button" className="ghost-button" onClick={closeSupportModal} disabled={supportSubmitting}>
              Close
            </button>
          </div>

          <form className="support-form" onSubmit={startSupportPayment}>
            <label className="label" htmlFor="donorName">Donor name</label>
            <input
              id="donorName"
              type="text"
              value={supportForm.donorName}
              onChange={(event) => setSupportForm({ ...supportForm, donorName: event.target.value })}
              required
              disabled={supportSubmitting}
            />

            <label className="label" htmlFor="donationAmount">Amount (INR)</label>
            <input
              id="donationAmount"
              type="number"
              min="1"
              step="1"
              value={supportForm.amount}
              onChange={(event) => setSupportForm({ ...supportForm, amount: event.target.value })}
              required
              disabled={supportSubmitting}
            />

            <p className="field-hint">Cards, UPI, netbanking, wallets, and other enabled payment modes are handled by the checkout provider.</p>

            <button className="primary-button auth-submit" type="submit" disabled={supportSubmitting}>
              {supportSubmitting ? "Starting payment..." : "Continue to payment"}
            </button>
          </form>

          {supportSuccess ? <p className="success-banner">{supportSuccess}</p> : null}
          {supportError ? <p className="error-banner">{supportError}</p> : null}
        </div>
      </div>
    );
  }
}

function MermaidDiagram({ chart }) {
  const [svgMarkup, setSvgMarkup] = useState("");
  const [renderError, setRenderError] = useState("");
  const [showSource, setShowSource] = useState(false);
  const [isExpanded, setIsExpanded] = useState(false);

  useEffect(() => {
    let cancelled = false;

    async function renderChart() {
      if (!chart || !chart.trim()) {
        setSvgMarkup("");
        setRenderError("");
        return;
      }

      try {
        const mermaidModule = await import("mermaid");
        const mermaid = mermaidModule.default;
        mermaid.initialize({
          startOnLoad: false,
          securityLevel: "loose",
          theme: "neutral"
        });
        const renderId = `mermaid-${Math.random().toString(36).slice(2)}`;
        const rendered = await mermaid.render(renderId, chart);
        if (!cancelled) {
          setSvgMarkup(rendered.svg);
          setRenderError("");
        }
      } catch (error) {
        if (!cancelled) {
          setSvgMarkup("");
          setRenderError("Unable to render this diagram visually right now. You can still use the Mermaid source below.");
        }
      }
    }

    renderChart();
    return () => {
      cancelled = true;
    };
  }, [chart]);

  return (
    <>
      <div className="diagram-render-shell">
        <div className="diagram-toolbar">
          {svgMarkup ? (
            <button
              type="button"
              className="ghost-button diagram-toggle"
              onClick={() => setIsExpanded(true)}
            >
              Open large view
            </button>
          ) : null}
          <button
            type="button"
            className="ghost-button diagram-toggle"
            onClick={() => setShowSource((value) => !value)}
          >
            {showSource ? "Hide source" : "Show source"}
          </button>
        </div>

        {svgMarkup ? (
          <button
            type="button"
            className="diagram-canvas diagram-canvas-button"
            onClick={() => setIsExpanded(true)}
            aria-label="Open large HLD diagram view"
          >
            <div dangerouslySetInnerHTML={{ __html: svgMarkup }} />
          </button>
        ) : (
          <div className="diagram-fallback-state">
            <p className="muted-copy">{renderError || "Rendering diagram..."}</p>
          </div>
        )}

        {showSource || renderError ? <pre className="diagram-block">{chart}</pre> : null}
      </div>

      {isExpanded && svgMarkup ? (
        <div className="diagram-modal-overlay" role="dialog" aria-modal="true" onClick={() => setIsExpanded(false)}>
          <div className="diagram-modal-card" onClick={(event) => event.stopPropagation()}>
            <div className="diagram-modal-header">
              <div>
                <p className="eyebrow">HLD diagram</p>
                <h3>Expanded diagram view</h3>
              </div>
              <button
                type="button"
                className="ghost-button"
                onClick={() => setIsExpanded(false)}
              >
                Close
              </button>
            </div>
            <div className="diagram-modal-canvas" dangerouslySetInnerHTML={{ __html: svgMarkup }} />
          </div>
        </div>
      ) : null}
    </>
  );
}

function SectionPane({ activeSection, sectionData }) {
  const sectionMeta = {
    functional: { title: "Functional requirements", type: "list" },
    nonFunctional: { title: "Non-functional requirements", type: "list" },
    entities: { title: "Entities", type: "list" },
    apis: { title: "APIs", type: "list" },
    diagram: { title: "Full HLD diagram", type: "diagram" },
    summary: { title: "Summary", type: "text" },
    tradeOffs: { title: "Trade-offs", type: "list" },
    followUps: { title: "Follow-up questions", type: "list" }
  };

  const meta = sectionMeta[activeSection];
  const value = sectionData?.[activeSection];

  return (
    <section className="section-panel">
      <div className="section-panel-header">
        <p className="eyebrow">{meta.title}</p>
      </div>
      {meta.type === "diagram" ? (
        <MermaidDiagram chart={value} />
      ) : meta.type === "text" ? (
        <p className="summary-copy">{value}</p>
      ) : (
        <ul className="content-list">
          {value.map((item) => (
            <li key={item}>{item}</li>
          ))}
        </ul>
      )}
    </section>
  );
}

function MetricCard({ label, value }) {
  return (
    <div className="metric-card">
      <span className="metric-label">{label}</span>
      <strong className="metric-value">{value}</strong>
    </div>
  );
}

function FeedbackList({ title, items }) {
  return (
    <div>
      <p className="feedback-title">{title}</p>
      <ul className="feedback-list">
        {items.map((item) => (
          <li key={item}>{item}</li>
        ))}
      </ul>
    </div>
  );
}

function EyeIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true" className="password-icon">
      <path d="M1.5 12s3.8-6.5 10.5-6.5S22.5 12 22.5 12 18.7 18.5 12 18.5 1.5 12 1.5 12Z" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
      <circle cx="12" cy="12" r="3.2" fill="none" stroke="currentColor" strokeWidth="1.8" />
    </svg>
  );
}

function EyeOffIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true" className="password-icon">
      <path d="M1.5 12s3.8-6.5 10.5-6.5c2.3 0 4.2.8 5.8 1.9" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M9.9 9.9A3.1 3.1 0 0 1 15 14.1" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M3 3l18 18" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
      <path d="M6.4 6.4C3.5 8.2 1.5 12 1.5 12s3.8 6.5 10.5 6.5c2.1 0 4-.6 5.5-1.5" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

function BusyNotice({ title, subtitle, compact = false }) {
  return (
    <div className={compact ? "busy-notice busy-notice-compact" : "busy-notice"}>
      <div className="busy-progress" aria-hidden="true">
        <span className="busy-progress-bar" />
      </div>
      <strong>{title}</strong>
      <p>{subtitle}</p>
    </div>
  );
}

export default App;
