// LandingPage.jsx

import { useEffect, useState } from "react";
import axios from "axios";
import { API_URL } from "../config";
import { useNavigate } from "react-router-dom";
import "./LandingPage.css";
import PageTransition from "../components/PageTransition";
import { motion } from "framer-motion";

function LandingPage() {

    const navigate = useNavigate();

    const [services, setServices] = useState([]);


    useEffect(() => {
        loadServices();
    }, []);


    const getIcon = (name) => {

        const text = name.toLowerCase();

        if (text.includes("birth")) return "👶";
        if (text.includes("death")) return "⚰️";
        if (text.includes("income")) return "🪙";
        if (text.includes("community")) return "📜";
        if (text.includes("marriage")) return "💍";
        if (text.includes("residence")) return "🏡";
        if (text.includes("nativity")) return "🏠";

        return "📄";
    };


    const loadServices = async () => {

        try {

            const response = await axios.get(
                `${API_URL}/services?page=0&size=50`
            );

            console.log(response.data);

            setServices(response.data.content);

        } catch (error) {

            console.error(error);

        }
    };


    return (
        <PageTransition>

            <div className="page-bg">

                <div className="landing-page">

                    <div className="hero-container">


                        {/* =================================
                            HERO CONTENT
                        ================================= */}

                        <div className="hero-content">


                            {/* BADGE */}

                            <span className="hero-badge">
                                Vinayaga E-Service Portal
                            </span>


                            {/* TITLE */}

                            <h1 className="hero-title">

                                Government Services,
                                <br />
                                Simplified.

                            </h1>


                            {/* DESCRIPTION */}

                            <p className="hero-subtitle">

                                Apply for certificates,
                                track requests,
                                upload documents and receive approvals
                                from anywhere.

                            </p>


                            {/* =================================
                                STATS
                            ================================= */}

                            <div className="hero-stats">

                                <div className="stat-card">

                                    <div>

                                        <h3>25+</h3>

                                        <p>Services</p>

                                    </div>

                                </div>


                                <div className="stat-card">

                                    <div>

                                        <h3>24/7</h3>

                                        <p>Availability</p>

                                    </div>

                                </div>


                                <div className="stat-card">

                                    <div>

                                        <h3>100%</h3>

                                        <p>Digital</p>

                                    </div>

                                </div>

                            </div>


                            {/* =================================
                                LOGIN BUTTONS
                            ================================= */}

                            <div className="hero-buttons">


                                <motion.button
                                    whileHover={{
                                        scale: 1.04
                                    }}
                                    whileTap={{
                                        scale: 0.96
                                    }}
                                    className="customer-btn"
                                    onClick={() =>
                                        navigate("/customer-login")
                                    }
                                >

                                    Citizen Login

                                </motion.button>


                                <motion.button
                                    whileHover={{
                                        scale: 1.04
                                    }}
                                    whileTap={{
                                        scale: 0.96
                                    }}
                                    className="staff-btn"
                                    onClick={() =>
                                        navigate("/login")
                                    }
                                >

                                    Staff Login

                                </motion.button>


                            </div>


                            {/* =================================
                                TRUST FEATURES
                            ================================= */}

                            <div className="trust-row">


                                <div className="trust-item">

                                    🔒

                                    <span>
                                        Secure
                                    </span>

                                </div>


                                <div className="trust-item">

                                    📱

                                    <span>
                                        Mobile Friendly
                                    </span>

                                </div>


                                <div className="trust-item">

                                    ✈️

                                    <span>
                                        Instant Tracking
                                    </span>

                                </div>


                            </div>

                        </div>


                        {/* =================================
                            SERVICE CARD
                            SIBLING OF HERO CONTENT
                        ================================= */}

                        <motion.div
                            className="hero-card"
                            whileHover={{
                                rotateY: 5,
                                rotateX: 5,
                                scale: 1.02
                            }}
                        >

                            <div className="service-preview">


                                {/* HEADER */}

                                <div className="preview-header">

                                    <span>
                                        Available Services
                                    </span>


                                    <span className="service-count">

                                        {services.length}+

                                    </span>

                                </div>


                                {/* SCROLL AREA */}

                                <div className="preview-scroll">

                                    <motion.div
                                        className="preview-track"
                                        animate={{
                                            y: ["0%", "-50%"]
                                        }}
                                        transition={{
                                            duration: 18,
                                            ease: "linear",
                                            repeat: Infinity
                                        }}
                                    >

                                        {[...services, ...services].map(
                                            (service, index) => (

                                                <div
                                                    className="preview-item"
                                                    key={index}
                                                >

                                                    <div className="preview-icon">

                                                        {getIcon(
                                                            service.serviceName
                                                        )}

                                                    </div>


                                                    <span>

                                                        {service.serviceName}

                                                    </span>

                                                </div>

                                            )
                                        )}

                                    </motion.div>

                                </div>

                            </div>

                        </motion.div>

                    </div>

                </div>

            </div>

        </PageTransition>
    );
}

export default LandingPage;