import {
    useParams,
    useNavigate
} from "react-router-dom";
import publicApi from "../../api/publicApi";import secureApi from "../../api/secureApi";
import CustomerNavbar from "../../components/CustomerNavbar";
//import { useParams } from "react-router-dom";
import "./ServiceDocuments.css";
import { useState, useEffect } from "react";
import axios from "axios";
import { API_URL } from "../../config";

function ServiceDocuments() {

    const { id } = useParams();
    const navigate = useNavigate();

    const [customerName, setCustomerName] =
        useState("");

    const [phoneNumber, setPhoneNumber] =
        useState(
            localStorage.getItem("customerPhone")
            || ""
        );
    const [dob,
        setDob] =
        useState("");

    const [requiredDocuments,
        setRequiredDocuments] =
        useState([]);

    const [uploadedFiles,
        setUploadedFiles] =
        useState({});
    const [previewUrls,
        setPreviewUrls] =
        useState({});
    const [fields, setFields] =
        useState([]);

    const [fieldValues, setFieldValues] =
        useState({});

    const [autoFillData,
        setAutoFillData] =
        useState({});

    const submitRequest = async () => {

        try {

            const token =
                localStorage.getItem("token");

            const response =
                await axios.post(
                    (`${API_URL}/requests`),
                    {
                        customerName,
                        phoneNumber:localStorage.getItem("customerPhone"),
                        serviceId: id
                    },
                    {
                        headers: {
                            Authorization:
                                `Bearer ${token}`
                        }
                    }
                );
            const requestId =
                response.data.id;

            const formResponses =
                fields.map(field => ({

                    requestId,

                    fieldId: field.id,

                    value:
                        fieldValues[field.id] || ""

                }));
            await axios.post(
                (`${API_URL}/service-form-responses`),
                formResponses,
                {
                    headers: {
                        Authorization: `Bearer ${localStorage.getItem("token")}`
                    }
                }
            );
           // console.log(response.data);
            const uploadFile = async (

                file,

                documentName

            ) => {
                if (!file) return;

                const formData = new FormData();

                formData.append(
                    "file",
                    file
                );

                formData.append(
                    "requestId",
                    requestId
                );
                formData.append("documentName", documentName);

                const token = localStorage.getItem("token");

                await axios.post(
                    (`${API_URL}/documents/upload`),
                    formData,
                    {
                        headers: {
                            Authorization:
                                `Bearer ${token}`,
                            "Content-Type":
                                "multipart/form-data"
                        }
                    }
                );
            };

            for (const document of requiredDocuments) {

                const file = uploadedFiles[document.documentName];

                if (file) {

                    await uploadFile(

                        file,

                        document.documentName

                    );

                }

            }

            alert(
                "Application Submitted Successfully"
            );

            navigate(
                "/my-requests"
            );
        } catch (error) {

            console.error(error);

            alert(
                "Request Submission Failed"
            );
        }
    };
    useEffect(() => {

        publicApi
            .get(`/services/${id}/documents`)
            .then(res => {
                setRequiredDocuments(res.data);
            })
            .catch(console.error);

    }, [id]);
    useEffect(() => {

        axios.get(
            `${API_URL}/service-form-fields/service/${id}/active`
        )
            .then(res => {

                setFields(
                    res.data
                );

            })
            .catch(console.error);

    }, [id]);
    useEffect(() => {

        setCustomerName(
            localStorage.getItem(
                "customerName"
            ) || ""
        );

        setDob(
            localStorage.getItem(
                "customerDob"
            ) || ""
        );

    }, []);
    useEffect(() => {

        const phoneNumber =
            localStorage.getItem(
                "customerPhone"
            );

        axios.get(
            `${API_URL}/customer-form-responses/autofill/${phoneNumber}`,
            {
                headers: {
                    Authorization: `Bearer ${localStorage.getItem("token")}`
                }
            }
        )
            .then(res => {

                setAutoFillData(
                    res.data
                );

            });

    }, []);
    return (
        <>
            <CustomerNavbar />

            <div className="page-bg">

                <div className="application-page">

                    <div className="application-container">

                        <div className="application-card">

                            <div className="application-header">

                                <div>

                                    <h2 className="application-title">

                                        Service Application

                                    </h2>

                                    <p className="application-subtitle">

                                        Complete the form below and upload the required documents to submit your application.

                                    </p>

                                </div>

                                <div className="application-service-id">

                                    Service ID #{id}

                                </div>

                            </div>

                            {/* Customer Details */}

                            <div className="application-section">

                                <div className="application-field">

                                    <label className="application-label">

                                        Full Name

                                    </label>

                                    <input
                                        className="application-input"
                                        value={customerName}
                                        readOnly
                                    />

                                </div>

                                <div className="application-field">

                                    <label className="application-label">

                                        Date of Birth

                                    </label>

                                    <input
                                        className="application-input"
                                        value={dob}
                                        readOnly
                                    />

                                </div>

                                <div className="application-field">

                                    <label className="application-label">

                                        Phone Number

                                    </label>

                                    <input
                                        className="application-input"
                                        value={phoneNumber}
                                        readOnly
                                    />

                                </div>

                            </div>

                            {/* Application Details */}
                            <h4 className="application-section-title">

                                Application Details

                            </h4>

                            <div className="application-fields-grid">

                                {fields.map(field => (

                                    <div
                                        key={field.id}
                                        className="application-field"
                                    >

                                        <label className="application-label">

                                            {field.fieldName}

                                        </label>

                                        <input

                                            className="application-input"

                                            type={
                                                field.fieldType === "NUMBER"
                                                    ? "number"
                                                    : field.fieldType === "DATE"
                                                        ? "date"
                                                        : "text"
                                            }

                                            value={
                                                fieldValues[field.id]
                                                ??
                                                autoFillData[field.fieldName]
                                                ??
                                                ""
                                            }

                                            onChange={(e)=>

                                                setFieldValues({

                                                    ...fieldValues,

                                                    [field.id]:
                                                    e.target.value

                                                })

                                            }

                                        />

                                    </div>

                                ))}

                            </div>


                            {/* Documents */}

                            <h4 className="application-section-title">

                                Required Documents

                            </h4>

                            <div className="documents-section">

                                {

                                    requiredDocuments.map(document => (

                                        <div
                                            key={document.id}
                                            className="document-upload-card"
                                        >

                                            <label className="application-label">

                                                {document.documentName}

                                            </label>

                                            <div className="upload-actions">

                                                {/* Upload */}

                                                <label className="upload-btn">

                                                    📁 Upload

                                                    <input

                                                        type="file"

                                                        accept=".pdf,.jpg,.jpeg,.png"

                                                        hidden

                                                        onChange={(e) => {

                                                            const file = e.target.files[0];

                                                            if (!file) return;

                                                            setUploadedFiles({

                                                                ...uploadedFiles,

                                                                [document.documentName]: file

                                                            });

                                                            setPreviewUrls({

                                                                ...previewUrls,

                                                                [document.documentName]:
                                                                    URL.createObjectURL(file)

                                                            });

                                                        }}

                                                    />

                                                </label>

                                                {/* Capture */}

                                                <label className="capture-btn">

                                                    📷 Capture

                                                    <input

                                                        type="file"

                                                        accept="image/*"

                                                        capture="environment"

                                                        hidden

                                                        onChange={(e) => {

                                                            const file = e.target.files[0];

                                                            if (!file) return;

                                                            setUploadedFiles({

                                                                ...uploadedFiles,

                                                                [document.documentName]: file

                                                            });

                                                            setPreviewUrls({

                                                                ...previewUrls,

                                                                [document.documentName]:
                                                                    URL.createObjectURL(file)

                                                            });

                                                        }}

                                                    />

                                                </label>

                                            </div>
                                            {

                                                uploadedFiles[document.documentName] && (

                                                    <div className="selected-file">

                                                        ✅ {uploadedFiles[document.documentName].name}

                                                        {

                                                            uploadedFiles[document.documentName]
                                                                .type
                                                                .startsWith("image/")

                                                            &&

                                                            <img

                                                                src={
                                                                    previewUrls[
                                                                        document.documentName
                                                                        ]
                                                                }

                                                                alt="Preview"

                                                                className="document-preview"

                                                            />

                                                        }

                                                    </div>

                                                )

                                            }
                                        </div>

                                    ))

                                }

                            </div>

                            <div className="application-actions">

                                <button

                                    className="submit-request-button"

                                    onClick={submitRequest}

                                >

                                    Submit Request

                                </button>

                            </div>

                        </div>

                    </div>

                </div>

            </div>

        </>
    );
}

export default ServiceDocuments;
