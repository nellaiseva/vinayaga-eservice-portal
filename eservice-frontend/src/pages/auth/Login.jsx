import { useState } from "react";
import axios from "axios";
import { useNavigate } from "react-router-dom";

import { login } from "../../services/authService";
import "./Logincss.css";

import { API_URL } from "../../config";
function Login() {
    const navigate = useNavigate();
    const [phoneNumber, setPhoneNumber] = useState("");
    const [password, setPassword] = useState("");
    const [forgotPassword, setForgotPassword] = useState(false);
    const [otpSent, setOtpSent] = useState(false);
    const [otpVerified, setOtpVerified] = useState(false);

    const [otp, setOtp] = useState("");
    const [newPassword, setNewPassword] = useState("");
    const [confirmPassword, setConfirmPassword] = useState("");

    const [loading, setLoading] = useState(false);
    const handleLogin = async () => {

        try {

            const response =
                await login(
                    phoneNumber,
                    password
                );
            localStorage.clear();
            localStorage.setItem(
                "token",
                response.token
            );

            localStorage.setItem(
                "role",
                response.role
            );
            localStorage.setItem(
                "phoneNumber",
                phoneNumber
            );
            if (response.employeeId) {

                localStorage.setItem(
                    "employeeId",
                    response.employeeId
                );
            }
            if (response.role === "OWNER") {

                navigate("/dashboard");

            }
            else if (
                response.role === "EMPLOYEE"
            ) {

                navigate("/employee-dashboard");


            }
            else {

                navigate("/customer-services");

            }

        }catch (error) {

            alert(

                error.response?.data?.message ||

                "Login failed"

            );

        }
    };
    const sendResetOtp = async () => {

        if (phoneNumber.length !== 10) {
            alert("Enter a valid 10-digit phone number.");
            return;
        }

        try {

            setLoading(true);

            const response = await axios.post(
                `${API_URL}/employee/forgot-password/send-otp`,
                {
                    phoneNumber
                }
            );

            if (response.data.success) {
                setOtpSent(true);
                alert(response.data.message);
            } else {
                alert(response.data.message);
            }

        } catch (error) {

            alert(
                error.response?.data?.message ||
                "Failed to send OTP"
            );

        } finally {
            setLoading(false);
        }
    };const verifyResetOtp = async () => {

        if (!otp) {
            alert("Enter the OTP.");
            return;
        }

        try {

            setLoading(true);

            const response = await axios.post(
                `${API_URL}/employee/forgot-password/verify-otp`,
                {
                    phoneNumber,
                    otp
                }
            );

            if (response.data.success) {
                setOtpVerified(true);
                alert(response.data.message);
            } else {
                alert(response.data.message);
            }

        } catch (error) {

            alert(
                error.response?.data?.message ||
                "Invalid OTP"
            );

        } finally {
            setLoading(false);
        }
    };const resetPassword = async () => {

        if (!newPassword) {
            alert("Enter a new password.");
            return;
        }

        if (newPassword !== confirmPassword) {
            alert("Passwords do not match.");
            return;
        }

        try {

            setLoading(true);

            const response = await axios.post(
                `${API_URL}/employee/forgot-password/reset`,
                {
                    phoneNumber,
                    newPassword
                }
            );

            if (response.data.success) {

                alert(
                    "Password reset successfully. Please login."
                );

                setForgotPassword(false);
                setOtpSent(false);
                setOtpVerified(false);

                setOtp("");
                setNewPassword("");
                setConfirmPassword("");

            } else {

                alert(response.data.message);
            }

        } catch (error) {

            alert(
                error.response?.data?.message ||
                "Failed to reset password"
            );

        } finally {
            setLoading(false);
        }
    };
    if (forgotPassword) {
        return (
            <div className="page-bg">

                <div className="login-page">

                    <div className="login-container">

                        <div className="login-card">

                            <div className="login-icon-wrapper">
                                <div className="login-icon">
                                    🔑
                                </div>
                            </div>

                            <h1 className="login-title">
                                Forgot Password
                            </h1>

                            <p className="login-subtitle">
                                Reset your staff account password securely.
                            </p>

                            <div className="login-input-group">

                                <input
                                    type="text"
                                    value={phoneNumber}
                                    maxLength={10}
                                    disabled={otpSent}
                                    onChange={(e) =>
                                        setPhoneNumber(
                                            e.target.value.replace(/\D/g, "")
                                        )
                                    }
                                    placeholder="Enter Phone Number"
                                    className="login-input"
                                />

                            </div>

                            {!otpSent && (

                                <button
                                    onClick={sendResetOtp}
                                    className="login-button"
                                    disabled={loading}
                                >
                                    {loading
                                        ? "Sending..."
                                        : "Send OTP →"
                                    }
                                </button>

                            )}

                            {otpSent && !otpVerified && (

                                <>
                                    <div className="login-input-group">

                                        <input
                                            type="text"
                                            value={otp}
                                            maxLength={6}
                                            onChange={(e) =>
                                                setOtp(
                                                    e.target.value.replace(/\D/g, "")
                                                )
                                            }
                                            placeholder="Enter OTP"
                                            className="login-input"
                                        />

                                    </div>

                                    <button
                                        onClick={verifyResetOtp}
                                        className="login-button"
                                        disabled={loading}
                                    >
                                        {loading
                                            ? "Verifying..."
                                            : "Verify OTP →"
                                        }
                                    </button>
                                </>
                            )}

                            {otpVerified && (

                                <>
                                    <div className="login-input-group">

                                        <input
                                            type="password"
                                            value={newPassword}
                                            onChange={(e) =>
                                                setNewPassword(e.target.value)
                                            }
                                            placeholder="Enter New Password"
                                            className="login-input"
                                        />

                                    </div>

                                    <div className="login-input-group">

                                        <input
                                            type="password"
                                            value={confirmPassword}
                                            onChange={(e) =>
                                                setConfirmPassword(e.target.value)
                                            }
                                            placeholder="Confirm New Password"
                                            className="login-input"
                                        />

                                    </div>

                                    <button
                                        onClick={resetPassword}
                                        className="login-button"
                                        disabled={loading}
                                    >
                                        {loading
                                            ? "Resetting..."
                                            : "Reset Password →"
                                        }
                                    </button>
                                </>
                            )}

                            <button
                                type="button"
                                className="forgot-password-link"
                                onClick={() => {

                                    setForgotPassword(false);
                                    setOtpSent(false);
                                    setOtpVerified(false);

                                    setOtp("");
                                    setNewPassword("");
                                    setConfirmPassword("");

                                }}
                            >
                                ← Back to Staff Login
                            </button>

                            <div className="login-footer">
                                🔒 Secure Password Recovery
                            </div>

                        </div>

                    </div>

                </div>

            </div>
        );
    }
    return (

        <div className="page-bg">

            <div className="login-page">

                <div className="login-container">

                    <div className="login-card">

                        {/* Icon */}

                        <div className="login-icon-wrapper">

                            <div className="login-icon">

                                🛡️

                            </div>

                        </div>

                        {/* Heading */}

                        <h1 className="login-title">

                            Staff Login

                        </h1>

                        <p className="login-subtitle">

                            Employee & Administrator Access

                        </p>

                        {/* Phone */}

                        <div className="login-input-group phone-group">

                            <input
                                type="text"
                                value={phoneNumber}
                                onChange={(e) =>
                                    setPhoneNumber(e.target.value)
                                }
                                placeholder="Enter Phone Number"
                                className="login-input"
                            />

                        </div>

                        {/* Password */}
                        <div className="login-input-group password-group">

                            <input
                                type="password"

                        value={password}
                        onChange={(e) =>
                        setPassword(e.target.value)
                    }
                        placeholder="Enter Password"
                        className="login-input"
                        />

                    </div>

                    {/* Login Button */}

                    <button
                        onClick={handleLogin}
                        className="login-button"
                    >

                        Login →

                    </button>

                        <button
                            type="button"
                            className="forgot-password-link"
                            onClick={() => setForgotPassword(true)}
                        >
                            Forgot Password?
                        </button>
                    <div className="login-footer">

                        🔒 Secure Staff Authentication

                    </div>

                </div>

            </div>

        </div>

</div>

)
    ;
}

export default Login;