import { useEffect } from "react";
import axios from "axios";
import { useNavigate } from "react-router-dom";
import "./CustomerProfileCheck.css";
import { API_URL } from "../../config";
import gear from "../../assets/gear.png";
function CustomerProfileCheck() {

    const navigate =
        useNavigate();

    useEffect(() => {

        const phoneNumber =
            localStorage.getItem(
                "customerPhone"
            );

        axios.get(
            `${API_URL}/customer/profile/${phoneNumber}`,
            {
                headers: {
                    Authorization: `Bearer ${localStorage.getItem("token")}`
                }
            }
        )
            .then(res => {

                if (res.data && res.data.customerName) {

                    localStorage.setItem(
                        "customerName",
                        res.data.customerName
                    );

                    localStorage.setItem(
                        "customerDob",
                        res.data.dob
                    );

                    navigate("/customer-services");

                } else {

                    navigate("/customer-profile");
                }

            })
            .catch(() => {

                navigate(
                    "/customer-profile"
                );

            });

    }, []);
    return (



        <div className="profile-check-loading">

            <div className="loading-card">
                <div className="glass-noise"></div>
                <div className="card-glow"></div>
                {/* Orbit Section */}
                <div className="orbit-wrapper">

                    <div className="orbit-ring ring-1"></div>
                    <div className="orbit-ring ring-2"></div>

                    <div className="orbit-dot dot-1"></div>
                    <div className="orbit-dot dot-2"></div>
                    <div className="orbit-dot dot-3"></div>

                    <span className="orbit verify">
                    Verify
                </span>

                    <span className="orbit analyze">
                    Analyze
                </span>

                    <span className="orbit complete">
                    Complete
                </span>

                    {/* Gear */}

                    <div className="gear-wrapper">

                        <img
                            src={gear}
                            alt="Gear"
                            className="gear-image"
                        />

                    </div>

                </div>

                {/* Content */}

                <div className="loading-content">

                    <h2>
                        Verifying Profile
                    </h2>

                    <p>
                        Please wait while we securely load your account.
                    </p>

                </div>

                {/* Status */}

                <div className="loading-status">

                    <div className="status-item">

                        <span className="status-dot"></span>

                        <span>Network</span>

                    </div>

                    <div className="status-item">

                        <span className="status-dot"></span>

                        <span>Credentials</span>

                    </div>

                    <div className="status-item">

                        <span className="status-dot"></span>

                        <span>Approval</span>

                    </div>

                </div>

            </div>

        </div>

    );}

export default CustomerProfileCheck;
