import CustomerNavbar
    from "../../components/CustomerNavbar";
import "./CustomerProfileView.css";
import { API_URL } from "../../config";
import { useEffect, useState }
    from "react";

import axios
    from "axios";

import {
    useNavigate
} from "react-router-dom";
function CustomerProfileView() {
    const [details,
        setDetails] =
        useState([]);

    const navigate =
        useNavigate();
    useEffect(() => {

        const phoneNumber =
            localStorage.getItem(
                "customerPhone"
            );

        axios.get(
            `${API_URL}/customer-form-responses/${phoneNumber}`,
            {
                headers: {
                    Authorization: `Bearer ${localStorage.getItem("token")}`
                }
            }
        )
            .then(res => {

                setDetails(
                    res.data
                );

            })
            .catch(
                console.error
            );

    }, []);
    return (
        <>
            <CustomerNavbar />

            <div className="customer-profile-page">

                <div className="profile-container">

                    {/* Main Card */}

                    <div className="profile-main-card">

                        {/* Header */}

                        <div className="profile-header">
                        </div>

                        {/* Avatar */}

                        <div className="profile-avatar">

                            <img
                                src="/images/profile.png"
                                alt="Profile"
                            />

                        </div>

                        {/* User Information */}

                        <div className="profile-info">

                            <h2 className="profile-name">

                                {
                                    localStorage.getItem(
                                        "customerName"
                                    )
                                }

                            </h2>

                            <p className="profile-role">

                                Citizen Profile

                            </p>

                            <div className="verified-badge">

                                ✓ Verified Citizen

                            </div>
                            <div className="edit-profile-wrapper">

                                <button
                                    onClick={() => navigate("/customer-profile-edit")}
                                    className="edit-profile-btn"
                                >
                                    ✏️ Edit Profile
                                </button>

                            </div>

                        </div>

                        {/* Content */}

                        <div className="profile-content">

                            <div className="profile-info-grid">

                                {/* Phone */}

                                <div className="profile-info-card">

                                    <div className="info-content">

                                        <div className="info-label">

                                            Phone Number

                                        </div>

                                        <div className="info-value">

                                            📞

                                            <span>

                                            {
                                                localStorage.getItem(
                                                    "customerPhone"
                                                )
                                            }

                                        </span>

                                        </div>

                                    </div>

                                    <div className="info-icon">

                                        📱

                                    </div>

                                </div>

                                {/* DOB */}

                                <div className="profile-info-card">

                                    <div className="info-content">

                                        <div className="info-label">

                                            Date of Birth

                                        </div>

                                        <div className="info-value">

                                            🎂

                                            <span>

                                            {
                                                localStorage.getItem(
                                                    "customerDob"
                                                )
                                            }

                                        </span>

                                        </div>

                                    </div>

                                    <div className="info-icon">

                                        🎉

                                    </div>

                                </div>

                            </div>

                            {/* Additional Information */}

                            <div className="additional-info-card">

                                <h3 className="additional-info-title">

                                    Additional Information

                                </h3>

                                {

                                    details.length === 0 ? (

                                        <div className="additional-empty">

                                            No additional details added yet.

                                        </div>

                                    ) : (

                                        <div className="additional-info-content">

                                            {

                                                details.map(detail => (

                                                    <div
                                                        key={detail.id}
                                                        className="additional-item"
                                                    >

                                                        <div className="additional-label">

                                                            {

                                                                detail.field.fieldName

                                                            }

                                                        </div>

                                                        <div className="additional-value">

                                                            {

                                                                detail.value

                                                            }

                                                        </div>

                                                    </div>

                                                ))

                                            }

                                        </div>

                                    )

                                }

                            </div>

                        </div>

                    </div>

                </div>

            </div>

        </>
    );
}

export default CustomerProfileView;
