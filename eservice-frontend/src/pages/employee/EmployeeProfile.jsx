import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";

import { getMyProfile } from "../../services/employeeProfileService";
import secureApi from "../../api/secureApi";
import EmployeeLayout from "../../layouts/EmployeeLayout";
import "./EmployeeProfile.css"
export default function EmployeeProfile() {

    const navigate = useNavigate();

    const [employee, setEmployee] = useState(null);
    const [profileImageUrl, setProfileImageUrl] = useState("");

    useEffect(() => {

        loadProfile();

    }, []);

    const loadProfile = async () => {

        const data = await getMyProfile();

        setEmployee(data);

        if (data.profileImage) {
            const response = await secureApi.get(
                `/employees/${data.id}/profile-image`,
                { responseType: "blob" }
            );

            setProfileImageUrl(URL.createObjectURL(response.data));
        }

    };

    if (!employee) {

        return (
            <EmployeeLayout>
                <div className="employee-profile-loading">
                    Loading...
                </div>
            </EmployeeLayout>
        );

    }

    return (

        <EmployeeLayout>

            <div className="page-bg">

                <div className="employee-profile-page">

                    <div className="employee-profile-container">

                        <div className="employee-profile-card">

                            {/* Banner */}

                            <div className="employee-profile-banner"></div>

                            {/* Profile Image */}

                            <div className="employee-profile-avatar">

                                <img
                                    src={
                                        profileImageUrl
                                            ? profileImageUrl
                                            : "/default-avatar.png"
                                    }
                                    alt="Profile"
                                    className="employee-profile-avatar-image"
                                />

                            </div>

                            {/* Edit Button */}

                            <button
                                onClick={() => navigate("/employee/profile/edit")}
                                className="employee-profile-edit-button"
                            >

                                ✏️ Edit Profile

                            </button>

                            {/* Name */}

                            <div className="employee-profile-info">

                                <h1 className="employee-profile-name">

                                    {employee.name}

                                </h1>

                                <p className="employee-profile-role">

                                    Employee Profile

                                </p>

                                <div className="employee-profile-status-wrapper">

                                <span className="employee-profile-status">

                                    ✓ Active Employee

                                </span>

                                </div>

                            </div>

                            {/* Details */}

                            <div className="employee-profile-grid">

                                <div className="employee-profile-item">

                                    <p className="employee-profile-label">

                                        Phone Number

                                    </p>

                                    <h3 className="employee-profile-value">

                                        {employee.phoneNumber}

                                    </h3>

                                </div>

                                <div className="employee-profile-item">

                                    <p className="employee-profile-label">

                                        Email

                                    </p>

                                    <h3 className="employee-profile-value">

                                        {employee.email || "-"}

                                    </h3>

                                </div>

                                <div className="employee-profile-item">

                                    <p className="employee-profile-label">

                                        Date Of Birth

                                    </p>

                                    <h3 className="employee-profile-value">

                                        {employee.dob || "-"}

                                    </h3>

                                </div>

                                <div className="employee-profile-item">

                                    <p className="employee-profile-label">

                                        Gender

                                    </p>

                                    <h3 className="employee-profile-value">

                                        {employee.gender || "-"}

                                    </h3>

                                </div>

                            </div>

                        </div>

                        {/* Additional Information */}

                        <div className="employee-profile-extra-card">

                            <h2 className="employee-profile-extra-title">

                                Additional Information

                            </h2>

                            <div className="employee-profile-extra-list">

                                <div className="employee-profile-extra-row">

                                <span className="employee-profile-extra-label">

                                    Address

                                </span>

                                    <span className="employee-profile-extra-value">

                                    {employee.address || "-"}

                                </span>

                                </div>

                                <div className="employee-profile-extra-row">

                                <span className="employee-profile-extra-label">

                                    Joined Date

                                </span>

                                    <span className="employee-profile-extra-value">

                                    {employee.joinedDate || "-"}

                                </span>

                                </div>

                                <div className="employee-profile-extra-row">

                                <span className="employee-profile-extra-label">

                                    Employee ID

                                </span>

                                    <span className="employee-profile-extra-value">

                                    #{employee.id}

                                </span>

                                </div>

                                <div className="employee-profile-extra-row employee-profile-extra-row-last">

                                <span className="employee-profile-extra-label">

                                    Status

                                </span>

                                    <span className="employee-profile-active-text">

                                    Active

                                </span>

                                </div>

                            </div>

                        </div>

                    </div>

                </div>

            </div>

        </EmployeeLayout>

    );
}
