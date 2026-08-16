import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import PageTransition from "../../components/PageTransition";
import { motion } from "framer-motion";
import "./CustomerLogin.css";import axios from "axios";

function CustomerLogin() {
    const [phoneNumber, setPhoneNumber] = useState(
        () => localStorage.getItem("otpPhoneNumber") || ""
    );

    const [otp, setOtp] = useState("");

    const [otpSent, setOtpSent] = useState(
        () => localStorage.getItem("otpSent") === "true"
    );

    const [loading, setLoading] = useState(false);

    const [timer, setTimer] = useState(() => {

        const cooldownUntil =
            Number(localStorage.getItem("otpCooldownUntil"));

        if (!cooldownUntil) {
            return 0;
        }

        const remaining =
            Math.ceil(
                (cooldownUntil - Date.now()) / 1000
            );

        return remaining > 0 ? remaining : 0;
    });

    const navigate =
        useNavigate();

    const API_URL =
        import.meta.env.VITE_API_URL ||
        "http://localhost:8080";
    useEffect(() => {

        if (!otpSent) {
            return;
        }

        const updateTimer = () => {

            const cooldownUntil =
                Number(
                    localStorage.getItem(
                        "otpCooldownUntil"
                    )
                );

            if (!cooldownUntil) {
                setTimer(0);
                return;
            }

            const remaining =
                Math.ceil(
                    (cooldownUntil - Date.now()) / 1000
                );

            if (remaining <= 0) {

                setTimer(0);

                localStorage.removeItem(
                    "otpCooldownUntil"
                );

            } else {

                setTimer(remaining);

            }
        };

        updateTimer();

        const interval =
            setInterval(updateTimer, 1000);

        return () => clearInterval(interval);

    }, [otpSent]);
    const sendOtp = async () => {

        if (phoneNumber.length !== 10) {

            alert("Enter a valid mobile number");

            return;
        }

        try {

            setLoading(true);

            const response = await axios.post(

                `${API_URL}/customer/send-otp`,

                {
                    phoneNumber
                }

            );

            if (response.data.success) {

                setOtpSent(true);

                localStorage.setItem(
                    "otpSent",
                    "true"
                );

                localStorage.setItem(
                    "otpPhoneNumber",
                    phoneNumber
                );

                const cooldownUntil =
                    Date.now() + 60 * 1000;

                localStorage.setItem(
                    "otpCooldownUntil",
                    cooldownUntil.toString()
                );

                setTimer(60);

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

    };

    const resendOtp = async () => {
        await sendOtp();
    };

    const verifyOtp = async () => {

        try {

            setLoading(true);

            const response = await axios.post(

                `${API_URL}/customer/verify-otp`,

                {

                    phoneNumber,

                    otp

                }

            );

            if (!response.data.success) {

                alert(response.data.message);

                return;

            }

            localStorage.setItem(

                "token",

                response.data.token

            );

            localStorage.setItem(

                "customerPhone",

                phoneNumber

            );

            localStorage.setItem(

                "phoneNumber",

                phoneNumber

            );
            localStorage.removeItem("otpSent");
            localStorage.removeItem("otpPhoneNumber");
            localStorage.removeItem("otpCooldownUntil");

            navigate("/customer-profile-check");

        } catch (error) {

            alert(

                error.response?.data?.message ||

                "Invalid OTP"

            );

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
                    initial={{
                        opacity: 0,
                        y: 40
                    }}
                    animate={{
                        opacity: 1,
                        y: 0
                    }}
                    transition={{
                        duration: 0.6
                    }}
                >

                    <div className="login-icon">
                        🏛️
                    </div>

                    <h1 className="login-title">
                        Citizen Login
                    </h1>

                    <p className="login-subtitle">
                        Access certificates, track requests
                        and manage documents securely.
                    </p>

                    <input
                        className="phone-input"
                        type="tel"
                        placeholder="Enter Mobile Number"
                        value={phoneNumber}
                        maxLength={10}
                        onChange={(e) =>
                            setPhoneNumber(
                                e.target.value.replace(/\D/g, "")
                            )
                        }
                    />
                    {
                        otpSent && (

                            <input
                                className="phone-input otp-input"
                                placeholder="Enter OTP"
                                value={otp}
                                onChange={(e) =>
                                    setOtp(
                                        e.target.value
                                    )
                                }
                            />


                        )
                    }

                    {
                        !otpSent ? (

                            <button
                                className="continue-btn"
                                onClick={sendOtp}
                                disabled={loading}
                            >
                                {
                                    loading
                                        ? "Sending..."
                                        : "Send OTP →"
                                }                            </button>


                        ) : (

                            <button
                                className="continue-btn"
                                onClick={verifyOtp}
                            >
                                {
                                    loading
                                        ? "Verifying..."
                                        : "Verify OTP →"
                                }                            </button>

                        )
                    }
                    {
                        otpSent && (
                            <div
                                style={{
                                    marginTop: "10px",
                                    textAlign: "center"
                                }}
                            >
                                {timer > 0 ? (
                                    <p
                                        style={{
                                            color: "#666",
                                            margin: 0
                                        }}
                                    >
                                        Resend OTP in {timer}s
                                    </p>
                                ) : (
                                    <button
                                        type="button"
                                        onClick={resendOtp}
                                        disabled={loading}
                                        style={{
                                            background: "none",
                                            border: "none",
                                            color: "#2457c5",
                                            cursor: "pointer",
                                            fontSize: "15px",
                                            fontWeight: "600"
                                        }}
                                    >
                                        {loading ? "Sending..." : "Resend OTP"}
                                    </button>
                                )}
                            </div>
                        )
                    }
                    <div className="login-footer">
                        🔒 Secure OTP Authentication
                    </div>
                </motion.div>

            </div>
            </div>
        </PageTransition>
    );
}

export default CustomerLogin;