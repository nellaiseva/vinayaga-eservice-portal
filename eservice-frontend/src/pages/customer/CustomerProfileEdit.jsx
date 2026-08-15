
import { useEffect, useState } from "react";
import { API_URL } from "../../config";
import "./CustomerProfileEdit.css";

import axios from "axios";

import {
    useNavigate
} from "react-router-dom";

import CustomerNavbar
    from "../../components/CustomerNavbar";

function CustomerProfileEdit() {

    const navigate =
        useNavigate();

    const [customerName,
        setCustomerName] =
        useState(
            localStorage.getItem(
                "customerName"
            )
        );

    const [dob,
        setDob] =
        useState(
            localStorage.getItem(
                "customerDob"
            )
        );
    const [fields, setFields] = useState([]);

    const [values, setValues] = useState({});
    useEffect(() => {

        axios.get(
            (`${API_URL}/customer-form-fields`)
        )
            .then(res => {

                setFields(res.data);

            })
            .catch(console.error);

    }, []);
    const updateProfile =
        async () => {

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
            for (const fieldId in values) {

                await axios.post(
                    (`${API_URL}/customer-form-responses`),
                    {
                        fieldId,
                        phoneNumber,
                        value: values[fieldId]
                    },
                    {
                        headers: {
                            Authorization: `Bearer ${localStorage.getItem("token")}`
                        }
                    }
                );
            }

            localStorage.setItem(
                "customerName",
                customerName
            );

            localStorage.setItem(
                "customerDob",
                dob
            );

            alert(
                "Profile Updated"
            );

            navigate(
                "/customer-profile-view"
            );
        };
    const handleChange = (
        fieldId,
        value
    ) => {

        setValues(prev => ({
            ...prev,
            [fieldId]: value
        }));

    };
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

                setCustomerName(
                    res.data.customerName
                );

                setDob(
                    res.data.dob
                );

            });

    }, []);
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

                const map = {};

                res.data.forEach(
                    item => {

                        map[
                            item.field.id
                            ] =
                            item.value;

                    }
                );

                setValues(
                    map
                );

            });

    }, []);
    return (
        <>
            <CustomerNavbar />

            <div className="customer-profile-page">

                <div className="profile-container">

                    {/* Header */}

                    <div className="profile-edit-header">

                        <h2 className="profile-edit-title">

                            Edit Profile

                        </h2>

                        <p className="profile-edit-subtitle">

                            Keep your details up to date before applying for services.

                        </p>

                    </div>

                    {/* Mobile Verified */}

                    <div className="verified-card">

                        <div className="verified-content">

                            <div className="verified-icon">

                                ✓

                            </div>

                            <div>

                                <p className="verified-title">

                                    Mobile Verified

                                </p>

                                <p className="verified-phone">

                                    {
                                        localStorage.getItem(
                                            "customerPhone"
                                        )
                                    }

                                </p>

                            </div>

                        </div>

                    </div>

                    {/* Basic Details */}

                    <div className="profile-form-card">

                        <h3 className="form-section-title">

                            Basic Details

                        </h3>

                        <div className="profile-form-grid">

                            <div className="form-group">

                                <label className="form-label">

                                    Full Name

                                </label>

                                <input

                                    className="form-input"

                                    value={customerName}

                                    onChange={(e) =>
                                        setCustomerName(
                                            e.target.value
                                        )
                                    }

                                />

                            </div>

                            <div className="form-group">

                                <label className="form-label">

                                    Date of Birth

                                </label>

                                <input

                                    type="date"

                                    className="form-input"

                                    value={dob}

                                    onChange={(e) =>
                                        setDob(
                                            e.target.value
                                        )
                                    }

                                />

                            </div>

                        </div>

                    </div>

                    {/* Additional Information */}

                    <div className="profile-form-card">

                        <h3 className="form-section-title">

                            Additional Information

                        </h3>

                        <div className="profile-form-grid">

                            {

                                fields.map(field => (

                                    <div
                                        key={field.id}
                                        className="form-group"
                                    >

                                        <label className="form-label">

                                            {field.fieldName}

                                        </label>

                                        <input

                                            type={
                                                field.fieldType === "NUMBER"
                                                    ? "number"
                                                    : "text"
                                            }

                                            className="form-input"

                                            value={
                                                values[field.id] || ""
                                            }

                                            onChange={(e) =>
                                                setValues({

                                                    ...values,

                                                    [field.id]:
                                                    e.target.value

                                                })
                                            }

                                        />

                                    </div>

                                ))

                            }

                        </div>

                    </div>

                    {/* Save Button */}

                    <button

                        onClick={updateProfile}

                        className="save-profile-button"

                    >

                        💾 Save Changes

                    </button>

                </div>

            </div>

        </>
    );
}

export default CustomerProfileEdit;
