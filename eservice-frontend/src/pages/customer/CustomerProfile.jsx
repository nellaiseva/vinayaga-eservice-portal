import { useState } from "react";
import axios from "axios";
import { useNavigate } from "react-router-dom";
import "./CustomerProfile.css";
import PageTransition from "../../components/PageTransition";
import { motion } from "framer-motion";
import { API_URL } from "../../config";
import profileIcon from "../../assets/gearicon.png";
function CustomerProfile() {

    const [customerName,
        setCustomerName] =
        useState("");

    const [dob,
        setDob] =
        useState("");

    const navigate =
        useNavigate();

    const saveProfile = async () => {

        const phoneNumber =
            localStorage.getItem(
                "customerPhone"
            );

        await axios.post(
            (`${API_URL}/customer/profile`),
            {
                phoneNumber,
                customerName,
                dob
            },
            {
                headers: {
                    Authorization: `Bearer ${localStorage.getItem("token")}`
                }
            }
        );

        localStorage.setItem(
            "customerName",
            customerName
        );

        localStorage.setItem(
            "customerDob",
            dob
        );

        navigate(
            "/customer-services"
        );
    };

    return (

        <PageTransition>
            <div className="page-bg">

            <div className="customer-profile-page">
                <motion.div
                    className="profile-card"
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

                    <div className="profile-icon">

                        <img
                            src={profileIcon}
                            alt="Profile"
                            className="profile-icon-image"
                        />

                    </div>


                    <h1 className="profile-title">
                        Complete Profile
                    </h1>

                    <p className="profile-subtitle">
                        Please complete your details
                        before applying for services.
                    </p>

                    <div className="verified-box">

                        <div className="verified-text">
                            ✅ Mobile Verified
                        </div>

                        <div>
                            {
                                localStorage.getItem(
                                    "customerPhone"
                                )
                            }
                        </div>

                    </div>

                    <div className="input-wrapper">

    <span className="input-icon">
        👤
    </span>

                        <input
                            className="profile-input"
                            placeholder="Enter Full Name"
                        value={customerName}
                        onChange={(e) =>
                            setCustomerName(
                                e.target.value
                            )
                        }
                        /></div>

                    <div className="input-wrapper">

    <span className="input-icon">
        📅
    </span>

                        <input
                            type="date"
                            className="profile-input"
                        value={dob}
                        onChange={(e) =>
                            setDob(
                                e.target.value
                            )
                        }
                    />
                    </div>
                    <button
                        className="save-profile-btn"
                        onClick={saveProfile}
                    >
                        Save Profile
                    </button>

                </motion.div>

            </div>
            </div>
        </PageTransition>
    );

}

export default CustomerProfile;
