// LandingPage.jsx - Nellai e-Seva Portal
import { useEffect, useState, useCallback, useRef, useMemo } from "react";
import axios from "axios";
import { API_URL } from "../config";
import { useNavigate } from "react-router-dom";
import "./LandingPage.css";
import PageTransition from "../components/PageTransition";
import { motion, AnimatePresence } from "framer-motion";

// Development-only mock services list (shown only when import.meta.env.DEV is true)
const DEV_MOCK_SERVICES = [
    {
        id: "mock-1",
        serviceName: "Birth Certificate Application",
        description: "Apply for official birth records and digital certificate.",
        active: true,
        isMock: true
    },
    {
        id: "mock-2",
        serviceName: "Community Certificate",
        description: "Caste and community verification for government schemes and admissions.",
        active: true,
        isMock: true
    },
    {
        id: "mock-3",
        serviceName: "Income Certificate",
        description: "Annual household income verification and certificate issuance.",
        active: true,
        isMock: true
    },
    {
        id: "mock-4",
        serviceName: "Nativity Certificate",
        description: "Proof of state residence and nativity validation.",
        active: true,
        isMock: true
    },
    {
        id: "mock-5",
        serviceName: "Residence Certificate",
        description: "Official proof of domicile and current local residence.",
        active: true,
        isMock: true
    },
    {
        id: "mock-6",
        serviceName: "Water Tax Payment",
        description: "Municipal corporation water charges bill settlement.",
        active: true,
        isMock: true
    },
    {
        id: "mock-7",
        serviceName: "Property Tax Payment",
        description: "Urban and rural civic property tax calculation and payment.",
        active: true,
        isMock: true
    },
    {
        id: "mock-8",
        serviceName: "Electricity Bill Payment",
        description: "TANGEDCO electricity bill verification and instant receipt.",
        active: true,
        isMock: true
    },
    {
        id: "mock-9",
        serviceName: "Pension Application",
        description: "Old age, widow, and disability social welfare pension schemes.",
        active: true,
        isMock: true
    },
    {
        id: "mock-10",
        serviceName: "Government Scheme Application",
        description: "Direct benefit transfer and state social welfare programs.",
        active: true,
        isMock: true
    },
    {
        id: "mock-11",
        serviceName: "Application Status Check",
        description: "Track real-time status of your e-Seva service requests.",
        active: true,
        isMock: true
    },
    {
        id: "mock-12",
        serviceName: "Online Document Printing",
        description: "Official PVC cards, certificates, and challan reprints.",
        active: true,
        isMock: true
    },
    {
        id: "mock-13",
        serviceName: "Document Scanning",
        description: "High-resolution proof upload and digital archive conversion.",
        active: true,
        isMock: true
    },
    {
        id: "mock-14",
        serviceName: "Passport Application Assistance",
        description: "Online appointment booking and prerequisite document verification.",
        active: true,
        isMock: true
    },
    {
        id: "mock-15",
        serviceName: "Land Record Request",
        description: "Patta, Chitta, FMB sketch, and revenue registry copies.",
        active: true,
        isMock: true
    }
];

// Six core categories represented in portal reference artwork
const SERVICE_CATEGORIES = [
    {
        id: "documents",
        title: "Documents",
        icon: "📄",
        tag: "Official Records",
        colorClass: "cat-blue",
        description:
            "Official documentation, legal record verification, certified copies, and affidavit processing.",
        features: [
            "Affidavits & Declarations",
            "Certified Record Copies",
            "Identity Proof Verification"
        ]
    },
    {
        id: "citizen-services",
        title: "Citizen Services",
        icon: "👥",
        tag: "Public Welfare",
        colorClass: "cat-green",
        description:
            "Public welfare schemes, civic registrations, pension aids, and citizen grievance assistance.",
        features: [
            "Welfare Scheme Enrollments",
            "Public Grievance Redressal",
            "Social Security Assistance"
        ]
    },
    {
        id: "online-applications",
        title: "Online Applications",
        icon: "💻",
        tag: "Fast & Paperless",
        colorClass: "cat-amber",
        description:
            "End-to-end digital application processing with instant submission and live status tracking.",
        features: [
            "Paperless Digital Submission",
            "Real-Time Application Status",
            "Automated SMS & Email Updates"
        ]
    },
    {
        id: "printing",
        title: "Printing",
        icon: "🖨️",
        tag: "Smart & Physical",
        colorClass: "cat-purple",
        description:
            "Smart ration cards, Aadhaar PVC printing, official certificate reprints, and receipt generation.",
        features: [
            "Smart Card Issuance",
            "PVC Card Printing",
            "Official Receipt Reproduction"
        ]
    },
    {
        id: "certificates",
        title: "Certificates",
        icon: "📜",
        tag: "Govt Issued",
        colorClass: "cat-pink",
        description:
            "Revenue and municipal certificates including Birth, Death, Income, Community, and Nativity.",
        features: [
            "Birth & Death Certificates",
            "Income & Community Proofs",
            "Nativity & First Graduate"
        ]
    }
];

function LandingPage() {
    const navigate = useNavigate();

    // Live API services state
    const [services, setServices] = useState([]);
    const [loadingServices, setLoadingServices] = useState(true);
    const [servicesError, setServicesError] = useState(null);

    // UI interactive states
    const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
    const [loginDropdownOpen, setLoginDropdownOpen] = useState(false);
    const [helpModalOpen, setHelpModalOpen] = useState(false);
    const [activeCategoryModal, setActiveCategoryModal] = useState(null);
    const [devMockPreview, setDevMockPreview] = useState(null);
    const [isPanelHovered, setIsPanelHovered] = useState(false);
    const [prefersReducedMotion, setPrefersReducedMotion] = useState(() =>
        typeof window !== "undefined" && window.matchMedia ? window.matchMedia("(prefers-reduced-motion: reduce)").matches : false
    );

    const loginDropdownRef = useRef(null);
    const headerRef = useRef(null);

    // Check system prefers-reduced-motion
    useEffect(() => {
        if (typeof window === "undefined" || !window.matchMedia) return;
        const mediaQuery = window.matchMedia("(prefers-reduced-motion: reduce)");
        const listener = (e) => setPrefersReducedMotion(e.matches);
        mediaQuery.addEventListener("change", listener);
        return () => mediaQuery.removeEventListener("change", listener);
    }, []);

    // Dynamic service icon mapping
    const getIcon = (name) => {
        if (!name) return "📄";
        const text = name.toLowerCase();

        if (text.includes("birth")) return "👶";
        if (text.includes("death")) return "⚰️";
        if (text.includes("income")) return "🪙";
        if (text.includes("community")) return "📜";
        if (text.includes("marriage")) return "💍";
        if (text.includes("residence")) return "🏡";
        if (text.includes("nativity")) return "🏠";
        if (text.includes("graduate")) return "🎓";
        if (text.includes("legal heir")) return "⚖️";
        if (text.includes("water")) return "💧";
        if (text.includes("property") || text.includes("land") || text.includes("patta")) return "🗺️";
        if (text.includes("electric") || text.includes("bill")) return "⚡";
        if (text.includes("pension") || text.includes("welfare") || text.includes("scheme")) return "🤝";
        if (text.includes("status") || text.includes("track")) return "🔍";
        if (text.includes("print") || text.includes("card")) return "🖨️";
        if (text.includes("scan")) return "📸";
        if (text.includes("passport")) return "🛂";
        if (text.includes("paytest") || text.includes("test")) return "💳";

        return "📄";
    };

    // Load dynamic services from backend API
    const fetchServices = useCallback(() => {
        setLoadingServices(true);
        setServicesError(null);
        axios
            .get(`${API_URL}/services?page=0&size=50`)
            .then((response) => {
                if (response.data && Array.isArray(response.data.content)) {
                    setServices(response.data.content);
                } else if (Array.isArray(response.data)) {
                    setServices(response.data);
                } else {
                    setServices([]);
                }
                setLoadingServices(false);
            })
            .catch((error) => {
                console.error("Failed to load services:", error);
                setServicesError("Unable to load latest services from server.");
                setLoadingServices(false);
            });
    }, []);

    useEffect(() => {
        let isMounted = true;
        axios
            .get(`${API_URL}/services?page=0&size=50`)
            .then((response) => {
                if (!isMounted) return;
                if (response.data && Array.isArray(response.data.content)) {
                    setServices(response.data.content);
                } else if (Array.isArray(response.data)) {
                    setServices(response.data);
                } else {
                    setServices([]);
                }
                setLoadingServices(false);
            })
            .catch((error) => {
                if (!isMounted) return;
                console.error("Failed to load services:", error);
                setServicesError("Unable to load latest services from server.");
                setLoadingServices(false);
            });

        return () => {
            isMounted = false;
        };
    }, []);

    // Combine real API services with dev-only mock services (import.meta.env.DEV only)
    const displayedServices = useMemo(() => {
        const list = Array.isArray(services) ? [...services] : [];

        // In production, display strictly real API services
        if (!import.meta.env.DEV) {
            return list;
        }

        // In development mode only: add mock services without duplicates
        const existingNames = new Set(
            list.map((s) => (s.serviceName || "").toLowerCase().trim())
        );

        const mockToAdd = DEV_MOCK_SERVICES.filter(
            (mock) => !existingNames.has(mock.serviceName.toLowerCase().trim())
        );

        return [...list, ...mockToAdd];
    }, [services]);

    // Close login dropdown & mobile menu on outside click or Escape key
    useEffect(() => {
        function handleClickOutside(event) {
            if (
                loginDropdownRef.current &&
                !loginDropdownRef.current.contains(event.target)
            ) {
                setLoginDropdownOpen(false);
            }
            if (
                headerRef.current &&
                !headerRef.current.contains(event.target)
            ) {
                setMobileMenuOpen(false);
            }
        }
        function handleKeyDown(event) {
            if (event.key === "Escape") {
                setLoginDropdownOpen(false);
                setMobileMenuOpen(false);
            }
        }
        document.addEventListener("pointerdown", handleClickOutside);
        document.addEventListener("keydown", handleKeyDown);
        return () => {
            document.removeEventListener("pointerdown", handleClickOutside);
            document.removeEventListener("keydown", handleKeyDown);
        };
    }, []);

    // Close mobile menu on resize to desktop
    useEffect(() => {
        function handleResize() {
            if (window.innerWidth > 900) {
                setMobileMenuOpen(false);
            }
        }
        window.addEventListener("resize", handleResize);
        return () => window.removeEventListener("resize", handleResize);
    }, []);

    // Track Application navigation logic (reuses existing customer routes)
    const handleTrackApplication = () => {
        setMobileMenuOpen(false);
        const token = localStorage.getItem("token");
        const role = localStorage.getItem("role");
        if (token && role === "CUSTOMER") {
            navigate("/my-requests");
        } else {
            navigate("/customer-login");
        }
    };

    // Scroll smoothly to section
    const scrollToSection = (id) => {
        setMobileMenuOpen(false);
        const el = document.getElementById(id);
        if (el) {
            el.scrollIntoView({ behavior: "smooth" });
        }
    };

    // Category click handler
    const handleCategoryClick = (category) => {
        setActiveCategoryModal(category);
    };

    // Service item click handler
    const handleServiceClick = (service) => {
        if (!service) return;

        // If this is a development-only mock service without an API endpoint:
        if (service.isMock) {
            setDevMockPreview(service);
            return;
        }

        // For real services returned by the backend API:
        const token = localStorage.getItem("token");
        const role = localStorage.getItem("role");
        if (token && role === "CUSTOMER") {
            if (service.id) {
                navigate(`/service-documents/${service.id}`);
            } else {
                navigate("/customer-services");
            }
        } else {
            navigate("/customer-login");
        }
    };

    return (
        <PageTransition>
            <div className="landing-root" id="top">
                {/* =====================================================
                    1. NAVIGATION BAR
                ===================================================== */}
                <header className="landing-navbar" ref={headerRef}>
                    <div className="landing-nav-container">
                        {/* Brand / Logo */}
                        <div
                            className="landing-brand"
                            onClick={() => scrollToSection("top")}
                            onKeyDown={(e) => {
                                if (e.key === "Enter" || e.key === " ") {
                                    e.preventDefault();
                                    scrollToSection("top");
                                }
                            }}
                            role="button"
                            tabIndex={0}
                            aria-label="Nellai e-Seva Home"
                        >
                            <div className="landing-brand-emblem">
                                <span className="emblem-letter">N</span>
                                <span className="emblem-dot"></span>
                            </div>
                            <div className="landing-brand-text">
                                <span className="brand-title">
                                    Nellai <strong>e-Seva</strong>
                                </span>
                                <span className="brand-subtitle">
                                    Citizen Services Portal
                                </span>
                            </div>
                        </div>

                        {/* Desktop Navigation Links */}
                        <nav
                            className="landing-nav-links"
                            aria-label="Main Navigation"
                        >
                            <button
                                type="button"
                                className="nav-item-btn"
                                onClick={() => scrollToSection("top")}
                            >
                                Home
                            </button>
                            <button
                                type="button"
                                className="nav-item-btn"
                                onClick={() =>
                                    scrollToSection("available-services-panel")
                                }
                            >
                                Services
                            </button>
                            <button
                                type="button"
                                className="nav-item-btn"
                                onClick={handleTrackApplication}
                            >
                                Track Application
                            </button>
                            <button
                                type="button"
                                className="nav-item-btn"
                                onClick={() => setHelpModalOpen(true)}
                            >
                                Help
                            </button>
                        </nav>

                        {/* Right Action: Login Controls */}
                        <div className="landing-nav-actions">
                            <div
                                className="login-dropdown-wrapper"
                                ref={loginDropdownRef}
                            >
                                <button
                                    type="button"
                                    className="nav-login-btn"
                                    onClick={() => {
                                        setLoginDropdownOpen((prev) => !prev);
                                        setMobileMenuOpen(false);
                                    }}
                                    aria-expanded={loginDropdownOpen}
                                    aria-haspopup="menu"
                                >
                                    <span>Login</span>
                                    <span className="dropdown-arrow">▾</span>
                                </button>

                                <AnimatePresence>
                                    {loginDropdownOpen && (
                                        <motion.div
                                            className="nav-dropdown-menu"
                                            initial={{ opacity: 0, y: -6, scale: 0.98 }}
                                            animate={{ opacity: 1, y: 0, scale: 1 }}
                                            exit={{ opacity: 0, y: -6, scale: 0.98 }}
                                            transition={{ duration: 0.16, ease: "easeOut" }}
                                            role="menu"
                                            aria-label="Login Options"
                                        >
                                            <div className="dropdown-menu-header">
                                                <span className="dropdown-menu-title">
                                                    Select Portal
                                                </span>
                                                <span className="dropdown-menu-badge">
                                                    Secure Access
                                                </span>
                                            </div>

                                            <div className="dropdown-items-group">
                                                <button
                                                    type="button"
                                                    className="dropdown-item citizen-item"
                                                    onClick={() => {
                                                        setLoginDropdownOpen(false);
                                                        navigate("/customer-login");
                                                    }}
                                                    role="menuitem"
                                                >
                                                    <span
                                                        className="dropdown-item-icon citizen-icon"
                                                        aria-hidden="true"
                                                    >
                                                        👤
                                                    </span>
                                                    <div className="dropdown-item-text">
                                                        <strong className="dropdown-item-title">
                                                            Citizen Login
                                                        </strong>
                                                        <span className="dropdown-item-desc">
                                                            Apply and track your service requests.
                                                        </span>
                                                    </div>
                                                    <span
                                                        className="dropdown-item-arrow"
                                                        aria-hidden="true"
                                                    >
                                                        →
                                                    </span>
                                                </button>

                                                <div
                                                    className="dropdown-divider"
                                                    role="separator"
                                                ></div>

                                                <button
                                                    type="button"
                                                    className="dropdown-item staff-item"
                                                    onClick={() => {
                                                        setLoginDropdownOpen(false);
                                                        navigate("/login");
                                                    }}
                                                    role="menuitem"
                                                >
                                                    <span
                                                        className="dropdown-item-icon staff-icon"
                                                        aria-hidden="true"
                                                    >
                                                        🛡️
                                                    </span>
                                                    <div className="dropdown-item-text">
                                                        <strong className="dropdown-item-title">
                                                            Staff Login
                                                        </strong>
                                                        <span className="dropdown-item-desc">
                                                            Access the staff portal securely.
                                                        </span>
                                                    </div>
                                                    <span
                                                        className="dropdown-item-arrow"
                                                        aria-hidden="true"
                                                    >
                                                        →
                                                    </span>
                                                </button>
                                            </div>
                                        </motion.div>
                                    )}
                                </AnimatePresence>
                            </div>

                            {/* Mobile Hamburger Button */}
                            <button
                                type="button"
                                className="mobile-menu-toggle"
                                onClick={() => {
                                    setMobileMenuOpen((prev) => !prev);
                                    setLoginDropdownOpen(false);
                                }}
                                aria-label="Toggle navigation menu"
                            >
                                {mobileMenuOpen ? "✕" : "☰"}
                            </button>
                        </div>
                    </div>

                    {/* Mobile Navigation Drawer */}
                    <AnimatePresence>
                        {mobileMenuOpen && (
                            <motion.div
                                className="mobile-nav-drawer"
                                initial={{ opacity: 0, height: 0 }}
                                animate={{ opacity: 1, height: "auto" }}
                                exit={{ opacity: 0, height: 0 }}
                                transition={{ duration: 0.25 }}
                            >
                                <div className="mobile-nav-inner">
                                    <button
                                        type="button"
                                        className="mobile-nav-link"
                                        onClick={() => scrollToSection("top")}
                                    >
                                        🏠 Home
                                    </button>
                                    <button
                                        type="button"
                                        className="mobile-nav-link"
                                        onClick={() =>
                                            scrollToSection(
                                                "available-services-panel"
                                            )
                                        }
                                    >
                                        ⚡ Services
                                    </button>
                                    <button
                                        type="button"
                                        className="mobile-nav-link"
                                        onClick={handleTrackApplication}
                                    >
                                        📍 Track Application
                                    </button>
                                    <button
                                        type="button"
                                        className="mobile-nav-link"
                                        onClick={() => {
                                            setMobileMenuOpen(false);
                                            setHelpModalOpen(true);
                                        }}
                                    >
                                        ❓ Help & Support
                                    </button>

                                    <div className="mobile-nav-divider"></div>

                                    <div className="mobile-login-actions">
                                        <button
                                            type="button"
                                            className="mobile-login-btn citizen-btn"
                                            onClick={() => {
                                                setMobileMenuOpen(false);
                                                navigate("/customer-login");
                                            }}
                                        >
                                            Citizen Login
                                        </button>
                                        <button
                                            type="button"
                                            className="mobile-login-btn staff-btn"
                                            onClick={() => {
                                                setMobileMenuOpen(false);
                                                navigate("/login");
                                            }}
                                        >
                                            Staff Login
                                        </button>
                                    </div>
                                </div>
                            </motion.div>
                        )}
                    </AnimatePresence>
                </header>

                {/* =====================================================
                    2. HERO SECTION
                    Available Services Box placed in bottom-right lake & bridge area
                ===================================================== */}
                <section className="landing-hero-section">
                    {/* Background Layer: Official landing-bg.png artwork */}
                    <div
                        className="landing-hero-backdrop"
                        aria-hidden="true"
                    ></div>

                    {/* Subtle Overlay to Guarantee AAA Text Contrast */}
                    <div
                        className="landing-hero-gradient-overlay"
                        aria-hidden="true"
                    ></div>

                    <div className="landing-hero-container">
                        {/* Hero Left Content: Badge, Headings, Buttons, Stats */}
                        <div className="landing-hero-content">
                            {/* Portal Badge */}
                            <div className="hero-badge-wrapper">
                                <span className="hero-badge">
                                    <span className="badge-pulse"></span>
                                    Nellai E-Seva Portal
                                </span>
                            </div>

                            {/* Main Two-Tone Headline */}
                            <h1 className="hero-title">
                                <span className="title-navy">Your Services.</span>
                                <br />
                                <span className="title-blue">Our Priority.</span>
                            </h1>

                            {/* Exact Description */}
                            <p className="hero-description">
                                Digital services made easy for every citizen.
                                Access government and citizen services online,
                                anytime, anywhere, in a simple, secure and
                                convenient way.
                            </p>

                            {/* Hero Login Actions */}
                            <div className="hero-actions-group">
                                <motion.button
                                    whileHover={{ scale: 1.03 }}
                                    whileTap={{ scale: 0.97 }}
                                    className="hero-btn-citizen"
                                    onClick={() => navigate("/customer-login")}
                                >
                                    <span>Citizen Login</span>
                                    <span className="btn-arrow">→</span>
                                </motion.button>

                                <motion.button
                                    whileHover={{ scale: 1.03 }}
                                    whileTap={{ scale: 0.97 }}
                                    className="hero-btn-staff"
                                    onClick={() => navigate("/login")}
                                >
                                    <span>Staff Login</span>
                                </motion.button>
                            </div>

                            {/* Statistics Cards */}
                            <div
                                className="hero-stats-row"
                                aria-label="Portal Statistics"
                            >
                                <div className="stat-card">
                                    <div className="stat-card-inner">
                                        <div className="stat-number-wrap">
                                            <span className="stat-number">
                                                25+
                                            </span>
                                            <span className="stat-indicator ind-blue"></span>
                                        </div>
                                        <p className="stat-label">Services</p>
                                    </div>
                                </div>

                                <div className="stat-card">
                                    <div className="stat-card-inner">
                                        <div className="stat-number-wrap">
                                            <span className="stat-number">
                                                24/7
                                            </span>
                                            <span className="stat-indicator ind-green"></span>
                                        </div>
                                        <p className="stat-label">
                                            Availability
                                        </p>
                                    </div>
                                </div>

                                <div className="stat-card">
                                    <div className="stat-card-inner">
                                        <div className="stat-number-wrap">
                                            <span className="stat-number">
                                                100%
                                            </span>
                                            <span className="stat-indicator ind-gold"></span>
                                        </div>
                                        <p className="stat-label">Digital</p>
                                    </div>
                                </div>
                            </div>
                        </div>

                        {/* Bottom-Right Lake & Bridge Area: Available Services Panel */}
                        {/* Bottom-Right Lake & Bridge Area: Horizontal Available Services Bar */}
                        <div
                            className="landing-hero-services-col"
                            id="available-services-panel"
                        >
                            <div className="lake-services-panel lake-horizontal-bar">
                                {/* Compact Fixed Header Above Scrolling Row */}
                                <div className="lake-bar-header">
                                    <div className="lake-bar-header-left">
                                        <span className="live-dot" aria-hidden="true"></span>
                                        <h3 className="lake-panel-title">
                                            Available Services
                                        </h3>
                                        <span className="lake-count-badge">
                                            {displayedServices.length > 0
                                                ? `${displayedServices.length}+`
                                                : "25+"}
                                        </span>
                                    </div>
                                    <div className="lake-bar-header-right">
                                        <span className="lake-status-pill">
                                            ● Live Portal
                                        </span>
                                        <span className="lake-bar-hint">
                                            Hover to pause
                                        </span>
                                    </div>
                                </div>

                                {/* Continuous Horizontal Auto-Scrolling Row */}
                                <div
                                    className="lake-horizontal-scroll-window"
                                    onMouseEnter={() => setIsPanelHovered(true)}
                                    onMouseLeave={() => setIsPanelHovered(false)}
                                    onTouchStart={() => setIsPanelHovered(true)}
                                    onTouchEnd={() => setIsPanelHovered(false)}
                                >                                    {/* Left & Right Gradient Fade Masks */}
                                    <div
                                        className="lake-fade-mask mask-left"
                                        aria-hidden="true"
                                    ></div>
                                    <div
                                        className="lake-fade-mask mask-right"
                                        aria-hidden="true"
                                    ></div>

                                    {loadingServices && displayedServices.length === 0 ? (
                                        <div className="lake-horizontal-loading">
                                            {[1, 2, 3].map((i) => (
                                                <div
                                                    key={i}
                                                    className="lake-skeleton-card"
                                                >
                                                    <span className="lake-skeleton-icon"></span>
                                                    <span className="lake-skeleton-text"></span>
                                                </div>
                                            ))}
                                        </div>
                                    ) : servicesError && displayedServices.length === 0 ? (
                                        <div className="lake-horizontal-error">
                                            <span>⚠️ {servicesError}</span>
                                            <button
                                                type="button"
                                                onClick={fetchServices}
                                                className="lake-retry-btn"
                                            >
                                                Retry
                                            </button>
                                        </div>
                                    ) : displayedServices.length === 0 ? (
                                        <div className="lake-horizontal-empty">
                                            <span>Services updating shortly.</span>
                                        </div>
                                    ) : (
                                        <div
                                            className="lake-horizontal-track"
                                            style={{
                                                animationDuration: `${Math.max(displayedServices.length * 3, 35)}s`,
                                                animationPlayState: isPanelHovered ? "paused" : "running"
                                            }}
                                        >
                                            {/* Group 1 */}
                                            <div className="lake-ticker-group">
                                                {displayedServices.map((service, index) => (
                                                    <div
                                                        key={`svc-a-${service.id || index}`}
                                                        className="lake-horizontal-card"
                                                        onClick={() =>
                                                            handleServiceClick(service)
                                                        }
                                                        onKeyDown={(e) => {
                                                            if (
                                                                e.key === "Enter" ||
                                                                e.key === " "
                                                            ) {
                                                                e.preventDefault();
                                                                handleServiceClick(service);
                                                            }
                                                        }}
                                                        role="button"
                                                        tabIndex={0}
                                                        title={
                                                            service.isMock
                                                                ? "Click for dev preview"
                                                                : "Click to apply"
                                                        }
                                                    >
                                                        <span className="lake-card-icon">
                                                            {getIcon(
                                                                service.serviceName
                                                            )}
                                                        </span>
                                                        <div className="lake-card-text">
                                                            <span className="lake-card-name">
                                                                {service.serviceName}
                                                            </span>
                                                            {service.isMock && (
                                                                <span className="lake-card-mock-tag">
                                                                    Preview
                                                                </span>
                                                            )}
                                                        </div>
                                                        <span
                                                            className="lake-card-arrow"
                                                            aria-hidden="true"
                                                        >
                                                            →
                                                        </span>
                                                    </div>
                                                ))}
                                            </div>

                                            {/* Group 2 (Duplicate for seamless loop) */}
                                            <div
                                                className="lake-ticker-group"
                                                aria-hidden="true"
                                            >
                                                {displayedServices.map((service, index) => (
                                                    <div
                                                        key={`svc-b-${service.id || index}`}
                                                        className="lake-horizontal-card"
                                                        onClick={() =>
                                                            handleServiceClick(service)
                                                        }
                                                        role="button"
                                                        tabIndex={-1}
                                                    >
                                                        <span className="lake-card-icon">
                                                            {getIcon(
                                                                service.serviceName
                                                            )}
                                                        </span>
                                                        <div className="lake-card-text">
                                                            <span className="lake-card-name">
                                                                {service.serviceName}
                                                            </span>
                                                            {service.isMock && (
                                                                <span className="lake-card-mock-tag">
                                                                    Preview
                                                                </span>
                                                            )}
                                                        </div>
                                                        <span
                                                            className="lake-card-arrow"
                                                            aria-hidden="true"
                                                        >
                                                            →
                                                        </span>
                                                    </div>
                                                ))}
                                            </div>
                                        </div>
                                    )}
                                </div>
                            </div>
                        </div>
                    </div>
                </section>

                {/* =====================================================
                    3. SERVICE CATEGORY CARDS SECTION (The 6 Reference Categories)
                ===================================================== */}
                <section
                    className="service-categories-section"
                    id="services-showcase"
                >
                    <div className="section-container">
                        <div className="section-header text-center">
                            <span className="section-eyebrow">
                                Core Capabilities
                            </span>
                            <h2 className="section-title">
                                Explore Service Categories
                            </h2>
                            <p className="section-description">
                                Designed for convenience and accessibility.
                                Select a category to discover available
                                services and streamlined digital procedures.
                            </p>
                        </div>

                        {/* 6 Category Responsive Grid */}
                        <div className="categories-grid">
                            {SERVICE_CATEGORIES.map((category) => (
                                <motion.div
                                    key={category.id}
                                    className={`category-card ${category.colorClass}`}
                                    whileHover={{ y: -6 }}
                                    transition={{ duration: 0.25 }}
                                    onClick={() =>
                                        handleCategoryClick(category)
                                    }
                                    onKeyDown={(e) => {
                                        if (e.key === "Enter" || e.key === " ") {
                                            e.preventDefault();
                                            handleCategoryClick(category);
                                        }
                                    }}
                                    role="button"
                                    tabIndex={0}
                                    aria-label={`${category.title} Category Details`}
                                >
                                    <div className="category-card-top">
                                        <div className="category-icon-wrapper">
                                            <span className="category-icon">
                                                {category.icon}
                                            </span>
                                        </div>
                                        <span className="category-tag">
                                            {category.tag}
                                        </span>
                                    </div>

                                    <h3 className="category-title">
                                        {category.title}
                                    </h3>
                                    <p className="category-description">
                                        {category.description}
                                    </p>

                                    <div className="category-footer">
                                        <span className="category-action-link">
                                            View Details
                                            <span className="link-arrow">
                                                →
                                            </span>
                                        </span>
                                    </div>
                                </motion.div>
                            ))}
                        </div>
                    </div>
                </section>

                {/* =====================================================
                    4. TRUST & ASSURANCE SECTION
                ===================================================== */}
                <section className="landing-trust-section">
                    <div className="section-container">
                        <div className="trust-grid">
                            <div className="trust-card">
                                <div className="trust-icon-box">
                                    <span className="trust-icon">⚡</span>
                                </div>
                                <div className="trust-content">
                                    <h4 className="trust-title">Simple</h4>
                                    <p className="trust-desc">
                                        Straightforward digital workflows
                                        designed for all citizens. Minimal
                                        steps, paperless requests, and instant
                                        receipts.
                                    </p>
                                </div>
                            </div>

                            <div className="trust-card">
                                <div className="trust-icon-box">
                                    <span className="trust-icon">🔒</span>
                                </div>
                                <div className="trust-content">
                                    <h4 className="trust-title">Secure</h4>
                                    <p className="trust-desc">
                                        End-to-end data protection, encrypted
                                        document handling, and dedicated
                                        role-based authentication for all users.
                                    </p>
                                </div>
                            </div>

                            <div className="trust-card">
                                <div className="trust-icon-box">
                                    <span className="trust-icon">🌐</span>
                                </div>
                                <div className="trust-content">
                                    <h4 className="trust-title">Accessible</h4>
                                    <p className="trust-desc">
                                        Available 24/7 across every device.
                                        Responsive mobile access ensuring
                                        reliable service anytime, anywhere.
                                    </p>
                                </div>
                            </div>
                        </div>
                    </div>
                </section>

                {/* =====================================================
                    5. FOOTER
                ===================================================== */}
                <footer className="landing-footer">
                    <div className="section-container footer-container">
                        <div className="footer-top-row">
                            <div className="footer-brand-col">
                                <div className="landing-brand footer-brand">
                                    <div className="landing-brand-emblem">
                                        <span className="emblem-letter">N</span>
                                    </div>
                                    <div className="landing-brand-text">
                                        <span className="brand-title">
                                            Nellai <strong>e-Seva</strong>
                                        </span>
                                        <span className="brand-subtitle">
                                            Government Citizen Services Portal
                                        </span>
                                    </div>
                                </div>
                                <p className="footer-tagline">
                                    Digital services made easy for every
                                    citizen. Fast, transparent, and convenient
                                    access to essential public services.
                                </p>
                            </div>

                            <div className="footer-links-col">
                                <h5 className="footer-col-title">
                                    Quick Links
                                </h5>
                                <ul className="footer-links-list">
                                    <li>
                                        <button
                                            type="button"
                                            onClick={() =>
                                                scrollToSection("top")
                                            }
                                        >
                                            Home
                                        </button>
                                    </li>
                                    <li>
                                        <button
                                            type="button"
                                            onClick={() =>
                                                scrollToSection(
                                                    "available-services-panel"
                                                )
                                            }
                                        >
                                            Available Services
                                        </button>
                                    </li>
                                    <li>
                                        <button
                                            type="button"
                                            onClick={handleTrackApplication}
                                        >
                                            Track Application
                                        </button>
                                    </li>
                                    <li>
                                        <button
                                            type="button"
                                            onClick={() =>
                                                setHelpModalOpen(true)
                                            }
                                        >
                                            Help & Support
                                        </button>
                                    </li>
                                </ul>
                            </div>

                            <div className="footer-links-col">
                                <h5 className="footer-col-title">Portals</h5>
                                <ul className="footer-links-list">
                                    <li>
                                        <button
                                            type="button"
                                            onClick={() =>
                                                navigate("/customer-login")
                                            }
                                        >
                                            Citizen Login
                                        </button>
                                    </li>
                                    <li>
                                        <button
                                            type="button"
                                            onClick={() =>
                                                navigate("/login")
                                            }
                                        >
                                            Staff Login
                                        </button>
                                    </li>
                                </ul>
                            </div>

                            <div className="footer-contact-col">
                                <h5 className="footer-col-title">Helpdesk</h5>
                                <p className="footer-contact-item">
                                    📞 <strong>7092542131</strong>
                                </p>
                                <p className="footer-contact-item">
                                    ✉️ <a href="mailto:nellaieseva@gmai.com" className="footer-contact-link">nellaieseva@gmai.com</a>
                                </p>
                                <p className="footer-contact-item">
                                    🕒 9 am to 9 pm
                                </p>
                                <p className="footer-contact-item footer-address-text">
                                    📍 Address: 9, Medai Police Station Street / 22A, Vaikalpalam, Near VAO Office II, Palayamkottai, Tirunelveli, Tamil Nadu 627002
                                </p>
                            </div>
                        </div>

                        <div className="footer-bottom-bar">
                            <p>
                                © {new Date().getFullYear()} Nellai e-Seva
                                Portal. All rights reserved.
                            </p>
                            <p className="footer-subtext">
                                Designed for simple, secure, and accessible
                                governance.
                            </p>
                        </div>
                    </div>
                </footer>

                {/* =====================================================
                    6. HELP & SUPPORT MODAL
                ===================================================== */}
                <AnimatePresence>
                    {helpModalOpen && (
                        <div
                            className="landing-modal-overlay"
                            onClick={() => setHelpModalOpen(false)}
                        >
                            <motion.div
                                className="landing-modal-box"
                                initial={{ opacity: 0, scale: 0.95 }}
                                animate={{ opacity: 1, scale: 1 }}
                                exit={{ opacity: 0, scale: 0.95 }}
                                transition={{ duration: 0.2 }}
                                onClick={(e) => e.stopPropagation()}
                                role="dialog"
                                aria-modal="true"
                            >
                                <div className="modal-header">
                                    <div className="modal-title-wrap">
                                        <span className="modal-icon">❓</span>
                                        <h3 className="modal-title">
                                            Nellai e-Seva Helpdesk
                                        </h3>
                                    </div>
                                    <button
                                        type="button"
                                        className="modal-close-btn"
                                        onClick={() =>
                                            setHelpModalOpen(false)
                                        }
                                    >
                                        ✕
                                    </button>
                                </div>

                                <div className="modal-body">
                                    <p className="modal-intro">
                                        Need assistance applying for a service
                                        or tracking an application? Our
                                        support team is here to assist you.
                                    </p>

                                    <div className="help-info-list">
                                        <div className="help-info-card">
                                            <span className="info-icon">
                                                📞
                                            </span>
                                            <div>
                                                <strong>
                                                    Contact Helpline
                                                </strong>
                                                <p>7092542131</p>
                                            </div>
                                        </div>

                                        <div className="help-info-card">
                                            <span className="info-icon">
                                                ✉️
                                            </span>
                                            <div>
                                                <strong>Email Support</strong>
                                                <p>
                                                    <a href="mailto:nellaieseva@gmai.com" className="help-contact-link">
                                                        nellaieseva@gmai.com
                                                    </a>
                                                </p>
                                            </div>
                                        </div>

                                        <div className="help-info-card">
                                            <span className="info-icon">
                                                🕒
                                            </span>
                                            <div>
                                                <strong>Support Hours</strong>
                                                <p>9 am to 9 pm</p>
                                            </div>
                                        </div>

                                        <div className="help-info-card">
                                            <span className="info-icon">
                                                📍
                                            </span>
                                            <div>
                                                <strong>Address</strong>
                                                <p>
                                                    Address: 22, Medai Police Station Street , Vaikalpalam, Near VAO Office II, Palayamkottai, Tirunelveli, Tamil Nadu 627002
                                                </p>
                                            </div>
                                        </div>
                                    </div>
                                </div>

                                <div className="modal-footer">
                                    <button
                                        type="button"
                                        className="modal-action-btn"
                                        onClick={() =>
                                            setHelpModalOpen(false)
                                        }
                                    >
                                        Close
                                    </button>
                                </div>
                            </motion.div>
                        </div>
                    )}
                </AnimatePresence>

                {/* =====================================================
                    7. CATEGORY DETAILS MODAL (Informational)
                ===================================================== */}
                <AnimatePresence>
                    {activeCategoryModal && (
                        <div
                            className="landing-modal-overlay"
                            onClick={() => setActiveCategoryModal(null)}
                        >
                            <motion.div
                                className="landing-modal-box"
                                initial={{ opacity: 0, scale: 0.95 }}
                                animate={{ opacity: 1, scale: 1 }}
                                exit={{ opacity: 0, scale: 0.95 }}
                                transition={{ duration: 0.2 }}
                                onClick={(e) => e.stopPropagation()}
                                role="dialog"
                                aria-modal="true"
                            >
                                <div className="modal-header">
                                    <div className="modal-title-wrap">
                                        <span className="modal-icon">
                                            {activeCategoryModal.icon}
                                        </span>
                                        <div>
                                            <h3 className="modal-title">
                                                {activeCategoryModal.title}
                                            </h3>
                                            <span className="category-modal-tag">
                                                {activeCategoryModal.tag}
                                            </span>
                                        </div>
                                    </div>
                                    <button
                                        type="button"
                                        className="modal-close-btn"
                                        onClick={() =>
                                            setActiveCategoryModal(null)
                                        }
                                    >
                                        ✕
                                    </button>
                                </div>

                                <div className="modal-body">
                                    <p className="category-modal-desc">
                                        {activeCategoryModal.description}
                                    </p>

                                    <h4 className="category-features-title">
                                        Key Services & Procedures:
                                    </h4>
                                    <ul className="category-features-list">
                                        {activeCategoryModal.features.map(
                                            (feat, idx) => (
                                                <li key={idx}>
                                                    <span className="check-bullet">
                                                        ✓
                                                    </span>
                                                    <span>{feat}</span>
                                                </li>
                                            )
                                        )}
                                    </ul>

                                    <div className="category-modal-callout">
                                        <p>
                                            Ready to apply? Log in with your
                                            registered mobile number to access
                                            all citizen services.
                                        </p>
                                    </div>
                                </div>

                                <div className="modal-footer modal-footer-split">
                                    <button
                                        type="button"
                                        className="modal-secondary-btn"
                                        onClick={() =>
                                            setActiveCategoryModal(null)
                                        }
                                    >
                                        Back
                                    </button>
                                    <button
                                        type="button"
                                        className="modal-primary-btn"
                                        onClick={() => {
                                            setActiveCategoryModal(null);
                                            navigate("/customer-login");
                                        }}
                                    >
                                        Apply via Citizen Login →
                                    </button>
                                </div>
                            </motion.div>
                        </div>
                    )}
                </AnimatePresence>

                {/* =====================================================
                    8. DEV MOCK SERVICE PREVIEW MODAL
                ===================================================== */}
                <AnimatePresence>
                    {devMockPreview && (
                        <div
                            className="landing-modal-overlay"
                            onClick={() => setDevMockPreview(null)}
                        >
                            <motion.div
                                className="landing-modal-box"
                                initial={{ opacity: 0, scale: 0.95 }}
                                animate={{ opacity: 1, scale: 1 }}
                                exit={{ opacity: 0, scale: 0.95 }}
                                transition={{ duration: 0.2 }}
                                onClick={(e) => e.stopPropagation()}
                                role="dialog"
                                aria-modal="true"
                            >
                                <div className="modal-header">
                                    <div className="modal-title-wrap">
                                        <span className="modal-icon">
                                            {getIcon(devMockPreview.serviceName)}
                                        </span>
                                        <div>
                                            <h3 className="modal-title">
                                                {devMockPreview.serviceName}
                                            </h3>
                                            <span
                                                className="category-modal-tag"
                                                style={{
                                                    background: "#e0e7ff",
                                                    color: "#4338ca"
                                                }}
                                            >
                                                Development Sample
                                            </span>
                                        </div>
                                    </div>
                                    <button
                                        type="button"
                                        className="modal-close-btn"
                                        onClick={() => setDevMockPreview(null)}
                                    >
                                        ✕
                                    </button>
                                </div>

                                <div className="modal-body">
                                    <p className="category-modal-desc">
                                        {devMockPreview.description}
                                    </p>

                                    <div className="category-modal-callout">
                                        <p>
                                            ℹ️ <strong>Development Mode:</strong>{" "}
                                            This service is shown in development
                                            (<code>import.meta.env.DEV</code>) to
                                            populate the scrolling list with
                                            16+ realistic citizen services. In
                                            production, only services configured
                                            in your live database will be
                                            displayed.
                                        </p>
                                    </div>
                                </div>

                                <div className="modal-footer">
                                    <button
                                        type="button"
                                        className="modal-action-btn"
                                        onClick={() => setDevMockPreview(null)}
                                    >
                                        Close Preview
                                    </button>
                                </div>
                            </motion.div>
                        </div>
                    )}
                </AnimatePresence>
            </div>
        </PageTransition>
    );
}

export default LandingPage;