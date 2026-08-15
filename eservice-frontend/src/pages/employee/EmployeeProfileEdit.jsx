import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";

import EmployeeLayout from "../../layouts/EmployeeLayout";

import {
    getMyProfile,
    updateMyProfile,
    uploadProfileImage
} from "../../services/employeeProfileService";

import secureApi from "../../api/secureApi";
import "./EmployeeProfileEdit.css"
function EmployeeProfileEdit() {

    const navigate = useNavigate();

    const [employee, setEmployee] = useState(null);

    const [selectedImage, setSelectedImage] = useState(null);

    const [preview, setPreview] = useState("");
    const [profileImageUrl, setProfileImageUrl] = useState("");
    const [saving, setSaving] = useState(false);
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

    const handleChange = (e) => {

        setEmployee({

            ...employee,

            [e.target.name]: e.target.value

        });

    };

    const handleSave = async () => {

        setSaving(true);

        try {

            await updateMyProfile(employee);

            if (selectedImage) {

                await uploadProfileImage(selectedImage);

            }

            navigate("/employee/profile");

        }

        finally {

            setSaving(false);

        }

    };

    if (!employee)
        return <div>Loading...</div>;

    return (

        <EmployeeLayout>

            <div className="page-bg">

                <div className="employee-edit-page">

                    <div className="employee-edit-container">

                        <div className="employee-edit-card">

                            {/* Banner */}

                            <div className="employee-edit-banner" />

                            {/* Header */}

                            <div className="employee-edit-header">

                                <div className="employee-edit-profile-section">

                                    <div className="employee-edit-avatar-wrapper">

                                        <img
                                            src={
                                                preview
                                                    ? preview
                                                    : profileImageUrl
                                                        ? profileImageUrl
                                                        : "/default-avatar.png"
                                            }
                                            alt="Profile"
                                            className="employee-edit-avatar"
                                        />

                                        <label
                                            htmlFor="profileImage"
                                            className="employee-edit-camera-button"
                                        >
                                            📷
                                        </label>

                                        <input
                                            id="profileImage"
                                            type="file"
                                            hidden
                                            onChange={(e) => {

                                                const file = e.target.files[0];

                                                if (!file) return;

                                                setSelectedImage(file);

                                                setPreview(
                                                    URL.createObjectURL(file)
                                                );

                                            }}
                                        />

                                    </div>

                                    <div className="employee-edit-header-content">

                                        <h1 className="employee-edit-title">

                                            Edit Profile

                                        </h1>

                                        <p className="employee-edit-subtitle">

                                            Update your information

                                        </p>

                                    </div>

                                </div>

                                <button
                                    onClick={() =>
                                        navigate("/employee/profile")
                                    }
                                    className="employee-edit-back-button"
                                >
                                    ← Back
                                </button>

                            </div>

                            <div className="employee-edit-form-grid">

                                {/* Name */}

                                <div>

                                    <label className="employee-edit-label">
                                        Name
                                    </label>

                                    <input
                                        name="name"
                                        value={employee.name || ""}
                                        onChange={handleChange}
                                        className="employee-edit-input"
                                    />

                                </div>

                                {/* Email */}

                                <div>

                                    <label className="employee-edit-label">
                                        Email
                                    </label>

                                    <input
                                        name="email"
                                        value={employee.email || ""}
                                        onChange={handleChange}
                                        className="employee-edit-input"
                                    />

                                </div>

                                {/* Phone */}

                                <div>

                                    <label className="employee-edit-label">
                                        Phone Number
                                    </label>

                                    <input
                                        value={employee.phoneNumber || ""}
                                        disabled
                                        className="employee-edit-input-disabled"
                                    />

                                </div>

                                {/* Date Of Birth */}

                                <div>

                                    <label className="employee-edit-label">
                                        Date of Birth
                                    </label>

                                    <input
                                        type="date"
                                        name="dob"
                                        value={employee.dob || ""}
                                        onChange={handleChange}
                                        className="employee-edit-input"
                                    />

                                </div>

                                {/* Gender */}

                                <div>

                                    <label className="employee-edit-label">
                                        Gender
                                    </label>

                                    <select
                                        name="gender"
                                        value={employee.gender || ""}
                                        onChange={handleChange}
                                        className="employee-edit-select"
                                    >

                                        <option value="">
                                            Select Gender
                                        </option>

                                        <option value="Male">
                                            Male
                                        </option>

                                        <option value="Female">
                                            Female
                                        </option>

                                        <option value="Other">
                                            Other
                                        </option>

                                    </select>

                                </div>

                                {/* Joining Date */}

                                <div>

                                    <label className="employee-edit-label">
                                        Date of Joining
                                    </label>

                                    <input
                                        type="date"
                                        value={employee.joinedDate || ""}
                                        disabled
                                        className="employee-edit-input-readonly"
                                    />

                                </div>

                                {/* Address */}

                                <div className="employee-edit-address-section">

                                    <label className="employee-edit-label">
                                        Address
                                    </label>

                                    <textarea
                                        rows={5}
                                        name="address"
                                        value={employee.address || ""}
                                        onChange={handleChange}
                                        className="employee-edit-textarea"
                                    />

                                </div>

                            </div>

                            {/* Footer */}

                            <div className="employee-edit-footer">

                                <button
                                    onClick={() =>
                                        navigate("/employee/profile")
                                    }
                                    className="employee-edit-cancel-button"
                                >
                                    ✕ Cancel
                                </button>

                                <button
                                    onClick={handleSave}
                                    disabled={saving}
                                    className="employee-edit-save-button"
                                >
                                    {saving ? "Saving..." : "✔ Save Changes"}
                                </button>

                            </div>

                        </div>

                    </div>

                </div>

            </div>

        </EmployeeLayout>

    );

}

export default EmployeeProfileEdit;
