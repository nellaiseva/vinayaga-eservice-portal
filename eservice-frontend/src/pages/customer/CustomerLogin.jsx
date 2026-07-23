import { useState } from "react";
import { useNavigate } from "react-router-dom";
import PageTransition from "../../components/PageTransition";
import { motion } from "framer-motion";
import "./CustomerLogin.css";import axios from "axios";

function CustomerLogin() {

    const [phoneNumber, setPhoneNumber] =
        useState("");

    const [otp, setOtp] =
        useState("");

    const [otpSent, setOtpSent] =
        useState(false);

    const navigate =
        useNavigate();
    const [loading, setLoading] = useState(false);

    const [timer, setTimer] = useState(0);
    const API_URL =
        import.meta.env.VITE_API_URL ||
        "http://localhost:8080";
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

                alert(response.data.message);

                setTimer(60);

                const interval = setInterval(() => {

                    setTimer(prev => {

                        if (prev <= 1) {

                            clearInterval(interval);

                            return 0;

                        }

                        return prev - 1;

                    });

                }, 1000);

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
                        otpSent && timer > 0 && (

                            <p
                                style={{
                                    marginTop: "10px",
                                    color: "#666"
                                }}
                            >
                                Resend OTP in {timer}s
                            </p>

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