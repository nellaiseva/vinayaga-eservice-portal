import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import PageTransition from "../../components/PageTransition";
import { motion } from "framer-motion";
import "./CustomerLogin.css";
import {
    login,
    checkCustomerAuthStatus,
    sendCustomerEnrollOtp,
    completeCustomerEnrollment,
    sendCustomerForgotOtp,
    resetCustomerPassword
} from "../../services/authService";

function CustomerLogin() {
    const [mode, setMode] = useState("PHONE"); // "PHONE" | "PASSWORD" | "UNENROLLED_OTP" | "UNENROLLED_PASSWORD" | "FORGOT_PASSWORD"
    const [phoneNumber, setPhoneNumber] = useState(
        () => localStorage.getItem("otpPhoneNumber") || ""
    );
    const [otp, setOtp] = useState("");
    const [password, setPassword] = useState("");
    const [confirmPassword, setConfirmPassword] = useState("");
    const [showPassword, setShowPassword] = useState(false);
    const [showConfirmPassword, setShowConfirmPassword] = useState(false);
    const [loading, setLoading] = useState(false);

    const [timer, setTimer] = useState(() => {
        const cooldownUntil = Number(localStorage.getItem("otpCooldownUntil"));
        if (!cooldownUntil) return 0;
        const remaining = Math.ceil((cooldownUntil - Date.now()) / 1000);
        return remaining > 0 ? remaining : 0;
    });

    const navigate = useNavigate();

    // Timer cooldown effect
    useEffect(() => {
        if (timer <= 0) return;
        const interval = setInterval(() => {
            const cooldownUntil = Number(localStorage.getItem("otpCooldownUntil"));
            if (!cooldownUntil) {
                setTimer(0);
                return;
            }
            const remaining = Math.ceil((cooldownUntil - Date.now()) / 1000);
            if (remaining <= 0) {
                setTimer(0);
                localStorage.removeItem("otpCooldownUntil");
            } else {
                setTimer(remaining);
            }
        }, 1000);
        return () => clearInterval(interval);
    }, [timer]);

    const startCooldown = () => {
        const cooldownUntil = Date.now() + 60 * 1000;
        localStorage.setItem("otpCooldownUntil", cooldownUntil.toString());
        setTimer(60);
    };

    const resetFields = () => {
        setOtp("");
        setPassword("");
        setConfirmPassword("");
        setShowPassword(false);
        setShowConfirmPassword(false);
    };

    // STEP 0: Check Phone Status
    const handleContinuePhone = async () => {
        if (loading) return;
        if (!/^[6-9]\d{9}$/.test(phoneNumber)) {
            alert("Please enter a valid 10-digit mobile number.");
            return;
        }

        try {
            setLoading(true);
            const statusRes = await checkCustomerAuthStatus(phoneNumber);

            if (statusRes.status === "STAFF_ACCOUNT") {
                alert(statusRes.message);
                return;
            }

            if (statusRes.status === "ENROLLED") {
                resetFields();
                setMode("PASSWORD");
            } else {
                // Not enrolled: auto-trigger enrollment OTP
                const otpRes = await sendCustomerEnrollOtp(phoneNumber);
                if (otpRes.success) {
                    startCooldown();
                    resetFields();
                    setMode("UNENROLLED_OTP");
                    alert(otpRes.message);
                } else {
                    alert(otpRes.message);
                }
            }
        } catch (error) {
            alert(
                error.response?.data?.message ||
                "Failed to verify mobile number. Please try again."
            );
        } finally {
            setLoading(false);
        }
    };

    // STEP 1: Password Login (Enrolled Customer)
    const handlePasswordLogin = async () => {
        if (loading) return;
        if (!password) {
            alert("Please enter your password.");
            return;
        }

        try {
            setLoading(true);
            const response = await login(phoneNumber, password);

            localStorage.setItem("token", response.token);
            localStorage.setItem("customerPhone", phoneNumber);
            localStorage.setItem("phoneNumber", phoneNumber);
            localStorage.setItem("role", "CUSTOMER");

            navigate("/customer-profile-check");
        } catch (error) {
            const status = error.response?.status;
            const msg = error.response?.data?.message || "Invalid credentials.";

            // If backend reports account is not enrolled, transition to enrollment
            if (status === 403 && msg.toLowerCase().includes("not enrolled")) {
                alert(msg);
                try {
                    const otpRes = await sendCustomerEnrollOtp(phoneNumber);
                    if (otpRes.success) {
                        startCooldown();
                        resetFields();
                        setMode("UNENROLLED_OTP");
                    } else {
                        alert(otpRes.message);
                    }
                } catch (e) {
                    alert(e.response?.data?.message || "Failed to send enrollment OTP.");
                }
            } else {
                alert(msg);
            }
        } finally {
            setLoading(false);
        }
    };

    // Resend Enrollment OTP
    const handleResendEnrollOtp = async () => {
        if (loading) return;
        try {
            setLoading(true);
            const res = await sendCustomerEnrollOtp(phoneNumber);
            if (res.success) {
                startCooldown();
                alert(res.message);
            } else {
                alert(res.message);
            }
        } catch (error) {
            alert(error.response?.data?.message || "Failed to resend OTP.");
        } finally {
            setLoading(false);
        }
    };

    // STEP 2: Transition from OTP input to Password creation
    const handleProceedToPasswordCreation = () => {
        if (loading) return;
        if (!/^\d{6}$/.test(otp)) {
            alert("Please enter a valid 6-digit OTP.");
            return;
        }
        setMode("UNENROLLED_PASSWORD");
    };

    // STEP 3: Complete Enrollment Atomically
    const handleCompleteEnrollment = async () => {
        if (loading) return;
        if (!password || password.length < 4) {
            alert("Password must be at least 4 characters long.");
            return;
        }
        if (password !== confirmPassword) {
            alert("Passwords do not match.");
            return;
        }

        try {
            setLoading(true);
            const res = await completeCustomerEnrollment(phoneNumber, otp, password);

            if (res.success) {
                localStorage.setItem("token", res.token);
                localStorage.setItem("customerPhone", phoneNumber);
                localStorage.setItem("phoneNumber", phoneNumber);
                localStorage.setItem("role", "CUSTOMER");

                alert("Enrollment successful! Redirecting to your account...");
                navigate("/customer-profile-check");
            } else {
                alert(res.message);
                if (res.message.toLowerCase().includes("otp")) {
                    setMode("UNENROLLED_OTP");
                }
            }
        } catch (error) {
            const msg = error.response?.data?.message || "Enrollment failed.";
            alert(msg);
            if (msg.toLowerCase().includes("otp")) {
                setMode("UNENROLLED_OTP");
            }
        } finally {
            setLoading(false);
        }
    };

    // Trigger Forgot Password flow
    const handleInitiateForgotPassword = async () => {
        if (loading) return;
        if (!/^[6-9]\d{9}$/.test(phoneNumber)) {
            alert("Please enter a valid 10-digit mobile number.");
            setMode("PHONE");
            return;
        }
        try {
            setLoading(true);
            const res = await sendCustomerForgotOtp(phoneNumber);
            if (res.success) {
                startCooldown();
                resetFields();
                setMode("FORGOT_PASSWORD");
                alert(res.message);
            } else {
                alert(res.message);
            }
        } catch (error) {
            alert(error.response?.data?.message || "Failed to send password reset OTP.");
        } finally {
            setLoading(false);
        }
    };

    // Resend Forgot Password OTP
    const handleResendForgotOtp = async () => {
        if (loading) return;
        try {
            setLoading(true);
            const res = await sendCustomerForgotOtp(phoneNumber);
            if (res.success) {
                startCooldown();
                alert(res.message);
            } else {
                alert(res.message);
            }
        } catch (error) {
            alert(error.response?.data?.message || "Failed to resend OTP.");
        } finally {
            setLoading(false);
        }
    };

    // Complete Password Reset
    const handleResetPassword = async () => {
        if (loading) return;
        if (!/^\d{6}$/.test(otp)) {
            alert("Please enter a valid 6-digit OTP.");
            return;
        }
        if (!password || password.length < 4) {
            alert("New password must be at least 4 characters long.");
            return;
        }
        if (password !== confirmPassword) {
            alert("Passwords do not match.");
            return;
        }

        try {
            setLoading(true);
            const res = await resetCustomerPassword(phoneNumber, otp, password);
            if (res.success) {
                alert("Password reset successfully! Please log in with your new password.");
                resetFields();
                setMode("PASSWORD");
            } else {
                alert(res.message);
            }
        } catch (error) {
            alert(error.response?.data?.message || "Failed to reset password.");
        } finally {
            setLoading(false);
        }
    };

    return (
        <PageTransition>
            <div className="page-bg">
                <div className="customer-login-page">
                    <motion.div
                        className="login-card"
                        initial={{ opacity: 0, y: 40 }}
                        animate={{ opacity: 1, y: 0 }}
                        transition={{ duration: 0.6 }}
                    >
                        <div className="login-icon">
                            🏛️
                        </div>

                        <h1 className="login-title">
                            Citizen Login
                        </h1>

                        <p className="login-subtitle">
                            {mode === "PHONE" && "Access certificates, track requests and manage documents securely."}
                            {mode === "PASSWORD" && "Welcome back! Enter your password to log in."}
                            {mode === "UNENROLLED_OTP" && "First-time setup: Enter the OTP sent to your mobile."}
                            {mode === "UNENROLLED_PASSWORD" && "Create a secure password for future logins."}
                            {mode === "FORGOT_PASSWORD" && "Reset your citizen account password securely."}
                        </p>

                        {/* =========================================================================
                            STATE 0: INITIAL PHONE INPUT
                            ========================================================================= */}
                        {mode === "PHONE" && (
                            <>
                                <input
                                    className="phone-input"
                                    type="tel"
                                    placeholder="Enter Mobile Number"
                                    value={phoneNumber}
                                    maxLength={10}
                                    onChange={(e) =>
                                        setPhoneNumber(e.target.value.replace(/\D/g, ""))
                                    }
                                    onKeyDown={(e) => {
                                        if (e.key === "Enter" && !loading) handleContinuePhone();
                                    }}
                                />

                                <button
                                    className="continue-btn"
                                    onClick={handleContinuePhone}
                                    disabled={loading}
                                >
                                    {loading ? "Checking..." : "Continue →"}
                                </button>
                            </>
                        )}

                        {/* =========================================================================
                            STATE 1: ENROLLED CUSTOMER PASSWORD LOGIN
                            ========================================================================= */}
                        {mode === "PASSWORD" && (
                            <>
                                <div className="phone-badge">
                                    <span>📱 +91 {phoneNumber}</span>
                                    <button
                                        type="button"
                                        className="phone-badge-change"
                                        disabled={loading}
                                        onClick={() => {
                                            resetFields();
                                            setMode("PHONE");
                                        }}
                                    >
                                        Change
                                    </button>
                                </div>

                                <div className="password-input-wrapper">
                                    <input
                                        className="phone-input"
                                        type={showPassword ? "text" : "password"}
                                        placeholder="Enter Password"
                                        value={password}
                                        onChange={(e) => setPassword(e.target.value)}
                                        onKeyDown={(e) => {
                                            if (e.key === "Enter" && !loading) handlePasswordLogin();
                                        }}
                                    />
                                    <button
                                        type="button"
                                        className="password-toggle-btn"
                                        onClick={() => setShowPassword(!showPassword)}
                                        aria-label="Toggle password visibility"
                                    >
                                        {showPassword ? "👁️" : "🙈"}
                                    </button>
                                </div>

                                <button
                                    className="continue-btn"
                                    onClick={handlePasswordLogin}
                                    disabled={loading}
                                >
                                    {loading ? "Logging in..." : "Login →"}
                                </button>

                                <div style={{ marginTop: "14px", textAlign: "center" }}>
                                    <button
                                        type="button"
                                        onClick={handleInitiateForgotPassword}
                                        disabled={loading}
                                        className="login-back-btn"
                                        style={{ marginTop: 0 }}
                                    >
                                        Forgot Password?
                                    </button>
                                </div>
                            </>
                        )}

                        {/* =========================================================================
                            STATE 2: UNENROLLED CUSTOMER - OTP VERIFICATION
                            ========================================================================= */}
                        {mode === "UNENROLLED_OTP" && (
                            <>
                                <div className="phone-badge">
                                    <span>📱 +91 {phoneNumber}</span>
                                    <button
                                        type="button"
                                        className="phone-badge-change"
                                        disabled={loading}
                                        onClick={() => {
                                            resetFields();
                                            setMode("PHONE");
                                        }}
                                    >
                                        Change
                                    </button>
                                </div>

                                <input
                                    className="phone-input otp-input"
                                    placeholder="Enter 6-digit OTP"
                                    value={otp}
                                    maxLength={6}
                                    onChange={(e) => setOtp(e.target.value.replace(/\D/g, ""))}
                                    onKeyDown={(e) => {
                                        if (e.key === "Enter" && !loading) handleProceedToPasswordCreation();
                                    }}
                                />

                                <button
                                    className="continue-btn"
                                    onClick={handleProceedToPasswordCreation}
                                    disabled={loading}
                                >
                                    Next: Set Password →
                                </button>

                                <div style={{ marginTop: "12px", textAlign: "center" }}>
                                    {timer > 0 ? (
                                        <p style={{ color: "#666", margin: 0, fontSize: "14px" }}>
                                             Resend OTP in {timer}s
                                        </p>
                                    ) : (
                                        <button
                                            type="button"
                                            onClick={handleResendEnrollOtp}
                                            disabled={loading}
                                            style={{
                                                background: "none",
                                                border: "none",
                                                color: "#2457c5",
                                                cursor: "pointer",
                                                fontSize: "14px",
                                                fontWeight: "600"
                                            }}
                                        >
                                            {loading ? "Sending..." : "Resend OTP"}
                                        </button>
                                    )}
                                </div>

                                <button
                                    type="button"
                                    className="login-back-btn"
                                    disabled={loading}
                                    onClick={() => {
                                        resetFields();
                                        setMode("PHONE");
                                    }}
                                >
                                    ← Change Mobile Number
                                </button>
                            </>
                        )}

                        {/* =========================================================================
                            STATE 3: UNENROLLED CUSTOMER - PASSWORD CREATION
                            ========================================================================= */}
                        {mode === "UNENROLLED_PASSWORD" && (
                            <>
                                <div className="phone-badge">
                                    <span>📱 +91 {phoneNumber}</span>
                                    <span style={{ fontSize: "13px", color: "#16a34a", fontWeight: "600" }}>
                                        OTP Entered
                                    </span>
                                </div>

                                <div className="password-input-wrapper">
                                    <input
                                        className="phone-input"
                                        type={showPassword ? "text" : "password"}
                                        placeholder="Create Password (min. 4 chars)"
                                        value={password}
                                        onChange={(e) => setPassword(e.target.value)}
                                        onKeyDown={(e) => {
                                            if (e.key === "Enter" && !loading) handleCompleteEnrollment();
                                        }}
                                    />
                                    <button
                                        type="button"
                                        className="password-toggle-btn"
                                        onClick={() => setShowPassword(!showPassword)}
                                        aria-label="Toggle password visibility"
                                    >
                                        {showPassword ? "👁️" : "🙈"}
                                    </button>
                                </div>

                                <div className="password-input-wrapper">
                                    <input
                                        className="phone-input otp-input"
                                        type={showConfirmPassword ? "text" : "password"}
                                        placeholder="Confirm Password"
                                        value={confirmPassword}
                                        onChange={(e) => setConfirmPassword(e.target.value)}
                                        onKeyDown={(e) => {
                                            if (e.key === "Enter" && !loading) handleCompleteEnrollment();
                                        }}
                                    />
                                    <button
                                        type="button"
                                        className="password-toggle-btn"
                                        style={{ top: "60%" }}
                                        onClick={() => setShowConfirmPassword(!showConfirmPassword)}
                                        aria-label="Toggle confirm password visibility"
                                    >
                                        {showConfirmPassword ? "👁️" : "🙈"}
                                    </button>
                                </div>

                                <button
                                    className="continue-btn"
                                    onClick={handleCompleteEnrollment}
                                    disabled={loading}
                                >
                                    {loading ? "Completing Setup..." : "Complete Enrollment & Login →"}
                                </button>

                                <button
                                    type="button"
                                    className="login-back-btn"
                                    disabled={loading}
                                    onClick={() => setMode("UNENROLLED_OTP")}
                                >
                                    ← Back to OTP
                                </button>
                            </>
                        )}

                        {/* =========================================================================
                            STATE 4: FORGOT PASSWORD FLOW
                            ========================================================================= */}
                        {mode === "FORGOT_PASSWORD" && (
                            <>
                                <div className="phone-badge">
                                    <span>📱 +91 {phoneNumber}</span>
                                    <span style={{ fontSize: "13px", color: "#2563eb", fontWeight: "600" }}>
                                        Password Reset
                                    </span>
                                </div>

                                <input
                                    className="phone-input otp-input"
                                    placeholder="Enter 6-digit OTP"
                                    value={otp}
                                    maxLength={6}
                                    onChange={(e) => setOtp(e.target.value.replace(/\D/g, ""))}
                                    onKeyDown={(e) => {
                                        if (e.key === "Enter" && !loading) handleResetPassword();
                                    }}
                                />

                                <div className="password-input-wrapper">
                                    <input
                                        className="phone-input"
                                        type={showPassword ? "text" : "password"}
                                        placeholder="New Password (min. 4 chars)"
                                        value={password}
                                        onChange={(e) => setPassword(e.target.value)}
                                        onKeyDown={(e) => {
                                            if (e.key === "Enter" && !loading) handleResetPassword();
                                        }}
                                    />
                                    <button
                                        type="button"
                                        className="password-toggle-btn"
                                        onClick={() => setShowPassword(!showPassword)}
                                        aria-label="Toggle password visibility"
                                    >
                                        {showPassword ? "👁️" : "🙈"}
                                    </button>
                                </div>

                                <div className="password-input-wrapper">
                                    <input
                                        className="phone-input otp-input"
                                        type={showConfirmPassword ? "text" : "password"}
                                        placeholder="Confirm New Password"
                                        value={confirmPassword}
                                        onChange={(e) => setConfirmPassword(e.target.value)}
                                        onKeyDown={(e) => {
                                            if (e.key === "Enter" && !loading) handleResetPassword();
                                        }}
                                    />
                                    <button
                                        type="button"
                                        className="password-toggle-btn"
                                        style={{ top: "60%" }}
                                        onClick={() => setShowConfirmPassword(!showConfirmPassword)}
                                        aria-label="Toggle confirm password visibility"
                                    >
                                        {showConfirmPassword ? "👁️" : "🙈"}
                                    </button>
                                </div>

                                <button
                                    className="continue-btn"
                                    onClick={handleResetPassword}
                                    disabled={loading}
                                >
                                    {loading ? "Resetting..." : "Reset Password & Login →"}
                                </button>

                                <div style={{ marginTop: "12px", textAlign: "center" }}>
                                    {timer > 0 ? (
                                        <p style={{ color: "#666", margin: 0, fontSize: "14px" }}>
                                            Resend OTP in {timer}s
                                        </p>
                                    ) : (
                                        <button
                                            type="button"
                                            onClick={handleResendForgotOtp}
                                            disabled={loading}
                                            style={{
                                                background: "none",
                                                border: "none",
                                                color: "#2457c5",
                                                cursor: "pointer",
                                                fontSize: "14px",
                                                fontWeight: "600"
                                            }}
                                        >
                                            {loading ? "Sending..." : "Resend OTP"}
                                        </button>
                                    )}
                                </div>

                                <button
                                    type="button"
                                    className="login-back-btn"
                                    disabled={loading}
                                    onClick={() => {
                                        resetFields();
                                        setMode("PASSWORD");
                                    }}
                                >
                                    ← Back to Login
                                </button>
                            </>
                        )}

                        <div className="login-footer">
                            {mode === "PHONE" && "🔒 Secure Citizen Portal"}
                            {mode === "PASSWORD" && "🔒 Secure Password Authentication"}
                            {(mode === "UNENROLLED_OTP" || mode === "UNENROLLED_PASSWORD") && "🔒 First-Time Citizen Enrollment"}
                            {mode === "FORGOT_PASSWORD" && "🔒 Secure Password Recovery"}
                        </div>
                    </motion.div>
                </div>
            </div>
        </PageTransition>
    );
}

export default CustomerLogin;