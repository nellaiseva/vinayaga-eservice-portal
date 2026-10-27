import {
    useParams,
    useNavigate
} from "react-router-dom";
import publicApi from "../../api/publicApi";
import CustomerNavbar from "../../components/CustomerNavbar";
import "./ServiceDocuments.css";
import { useState, useEffect } from "react";
import axios from "axios";
import { API_URL } from "../../config";

const MAX_FILE_SIZE = 30 * 1024 * 1024; // 30 MB
const ALLOWED_EXTENSIONS = [".pdf", ".jpg", ".jpeg", ".png", ".heic", ".heif"];
const ALLOWED_MIME_TYPES = [
    "application/pdf",
    "image/jpeg",
    "image/png",
    "image/jpg",
    "image/heic",
    "image/heif"
];

function ServiceDocuments() {

    const { id } = useParams();
    const navigate = useNavigate();

    const [customerName] = useState(
        () => localStorage.getItem("customerName") || ""
    );
    const [phoneNumber] = useState(
        () => localStorage.getItem("customerPhone") || ""
    );
    const [dob] = useState(
        () => localStorage.getItem("customerDob") || ""
    );

    const [requiredDocuments, setRequiredDocuments] = useState([]);
    const [documentGroups, setDocumentGroups] = useState({});
    const [fields, setFields] = useState([]);
    const [fieldValues, setFieldValues] = useState({});
    const [autoFillData, setAutoFillData] = useState({});
    const [isSubmitting, setIsSubmitting] = useState(false);
    const [submissionError, setSubmissionError] = useState("");

    const validateFile = (file) => {
        if (!file) return "Please select a file.";
        if (file.size > MAX_FILE_SIZE) {
            return "File size exceeds maximum allowed limit of 30 MB.";
        }
        const lowerName = file.name ? file.name.toLowerCase() : "";
        const matchesExt = ALLOWED_EXTENSIONS.some(ext => lowerName.endsWith(ext));
        const matchesType = file.type ? ALLOWED_MIME_TYPES.includes(file.type.toLowerCase()) : true;
        if (!matchesExt && !matchesType) {
            return "Unsupported file type. Please upload PDF, JPG, PNG, or HEIC.";
        }
        return null;
    };

    const getEntries = (docName) => {
        return (
            documentGroups[docName] && documentGroups[docName].length > 0
                ? documentGroups[docName]
                : [{ id: `${docName}_default`, file: null, previewUrl: null, error: null }]
        );
    };

    const addDocumentField = (docName) => {
        setDocumentGroups(prev => {
            const current = prev[docName] && prev[docName].length > 0
                ? prev[docName]
                : [{ id: `${docName}_0`, file: null, previewUrl: null, error: null }];

            const newEntry = {
                id: `${docName}_${Date.now()}_${current.length}`,
                file: null,
                previewUrl: null,
                error: null
            };
            return {
                ...prev,
                [docName]: [...current, newEntry]
            };
        });
    };

    const removeDocumentField = (docName, entryId) => {
        setDocumentGroups(prev => {
            const current = prev[docName] || [];
            if (current.length <= 1) return prev; // Never allow removing the last remaining field

            const target = current.find(e => e.id === entryId);
            if (target && target.previewUrl) {
                try {
                    URL.revokeObjectURL(target.previewUrl);
                } catch {
                    // Ignore object URL cleanup errors
                }
            }

            return {
                ...prev,
                [docName]: current.filter(e => e.id !== entryId)
            };
        });
    };

    const handleFileChange = (docName, entryId, file) => {
        if (!file) return;

        const validationErr = validateFile(file);

        setDocumentGroups(prev => {
            const current = prev[docName] ? [...prev[docName]] : [];
            const updated = current.map(entry => {
                if (entry.id !== entryId) return entry;

                if (entry.previewUrl) {
                    try {
                        URL.revokeObjectURL(entry.previewUrl);
                    } catch {
                        // Ignore object URL cleanup errors
                    }
                }

                if (validationErr) {
                    return {
                        ...entry,
                        file: null,
                        previewUrl: null,
                        error: validationErr
                    };
                }

                const isImage = file.type?.startsWith("image/")
                    || /\.(jpe?g|png|heic|heif)$/i.test(file.name);

                return {
                    ...entry,
                    file,
                    previewUrl: isImage ? URL.createObjectURL(file) : null,
                    error: null
                };
            });

            return {
                ...prev,
                [docName]: updated
            };
        });
    };

    const submitRequest = async () => {
        if (isSubmitting) return;
        setSubmissionError("");

        // Validation: Every required document type must have at least one uploaded file
        let hasErrors = false;
        const updatedGroups = { ...documentGroups };

        for (const doc of requiredDocuments) {
            const entries = getEntries(doc.documentName);
            const uploadedCount = entries.filter(entry => entry.file != null).length;

            if (uploadedCount === 0) {
                hasErrors = true;
                entries[0] = {
                    ...entries[0],
                    error: `Please upload at least one ${doc.documentName}.`
                };
            }

            // Check if any additional field was created but left empty
            entries.forEach((entry, idx) => {
                if (idx > 0 && !entry.file) {
                    hasErrors = true;
                    entries[idx] = {
                        ...entry,
                        error: "Please select a file or remove this extra field."
                    };
                }
            });

            updatedGroups[doc.documentName] = [...entries];
        }

        if (hasErrors) {
            setDocumentGroups(updatedGroups);
            setSubmissionError("Please resolve the required document errors before submitting.");
            return;
        }

        setIsSubmitting(true);

        try {
            const token = localStorage.getItem("token");

            const response = await axios.post(
                `${API_URL}/requests`,
                {
                    customerName,
                    phoneNumber: localStorage.getItem("customerPhone"),
                    serviceId: id
                },
                {
                    headers: {
                        Authorization: `Bearer ${token}`
                    }
                }
            );
            const requestId = response.data.id;

            const formResponses = fields.map(field => ({
                requestId,
                fieldId: field.id,
                value: fieldValues[field.id] || ""
            }));

            if (formResponses.length > 0) {
                await axios.post(
                    `${API_URL}/service-form-responses`,
                    formResponses,
                    {
                        headers: {
                            Authorization: `Bearer ${token}`
                        }
                    }
                );
            }

            const uploadFile = async (file, documentName, documentType) => {
                if (!file) return;

                const formData = new FormData();
                formData.append("file", file);
                formData.append("requestId", requestId);
                formData.append("documentName", documentName);
                if (documentType) {
                    formData.append("documentType", documentType);
                }

                await axios.post(
                    `${API_URL}/documents/upload`,
                    formData,
                    {
                        headers: {
                            Authorization: `Bearer ${token}`,
                            "Content-Type": "multipart/form-data"
                        }
                    }
                );
            };

            for (const document of requiredDocuments) {
                const entries = getEntries(document.documentName);
                const validEntries = entries.filter(e => e.file != null);

                for (let i = 0; i < validEntries.length; i++) {
                    const entry = validEntries[i];
                    const label = validEntries.length > 1
                        ? `${document.documentName} ${i + 1}`
                        : document.documentName;

                    await uploadFile(
                        entry.file,
                        label,
                        document.documentName
                    );
                }
            }

            alert("Application Submitted Successfully");
            navigate("/my-requests");
        } catch (error) {
            console.error(error);
            const msg = error.response?.data?.message || "Request Submission Failed";
            setSubmissionError(msg);
            alert(msg);
        } finally {
            setIsSubmitting(false);
        }
    };

    useEffect(() => {
        publicApi
            .get(`/services/${id}/documents`)
            .then(res => {
                const docs = res.data || [];
                setRequiredDocuments(docs);
                setDocumentGroups(prev => {
                    const next = { ...prev };
                    docs.forEach(doc => {
                        if (!next[doc.documentName] || next[doc.documentName].length === 0) {
                            next[doc.documentName] = [
                                {
                                    id: `${doc.id || doc.documentName}_0`,
                                    file: null,
                                    previewUrl: null,
                                    error: null
                                }
                            ];
                        }
                    });
                    return next;
                });
            })
            .catch(console.error);
    }, [id]);

    useEffect(() => {
        axios.get(
            `${API_URL}/service-form-fields/service/${id}/active`
        )
            .then(res => {
                setFields(res.data);
            })
            .catch(console.error);
    }, [id]);

    useEffect(() => {
        const phone = localStorage.getItem("customerPhone");
        if (!phone) return;

        axios.get(
            `${API_URL}/customer-form-responses/autofill/${phone}`,
            {
                headers: {
                    Authorization: `Bearer ${localStorage.getItem("token")}`
                }
            }
        )
            .then(res => {
                setAutoFillData(res.data);
            })
            .catch(console.error);
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
                            {fields.length > 0 && (
                                <>
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
                                                    onChange={(e) =>
                                                        setFieldValues({
                                                            ...fieldValues,
                                                            [field.id]: e.target.value
                                                        })
                                                    }
                                                />

                                            </div>

                                        ))}

                                    </div>
                                </>
                            )}

                            {/* Documents */}

                            <h4 className="application-section-title">
                                Required Documents
                            </h4>

                            {submissionError && (
                                <div className="submission-error-banner">
                                    ⚠️ {submissionError}
                                </div>
                            )}

                            <div className="documents-section">

                                {requiredDocuments.map(document => {
                                    const entries = getEntries(document.documentName);

                                    return (
                                        <div
                                            key={document.id}
                                            className="document-upload-card"
                                        >
                                            <div className="document-type-header">
                                                <label className="application-label">
                                                    {document.documentName}
                                                </label>
                                                <span className="document-type-badge">
                                                    Required
                                                </span>
                                            </div>

                                            <div className="document-instances-list">
                                                {entries.map((entry, index) => {
                                                    const fieldLabel = entries.length > 1
                                                        ? `${document.documentName} ${index + 1}`
                                                        : `${document.documentName} 1`;

                                                    return (
                                                        <div
                                                            key={entry.id}
                                                            className="document-instance-box"
                                                        >
                                                            <div className="document-instance-header">
                                                                <span className="document-instance-title">
                                                                    📄 {fieldLabel}
                                                                </span>

                                                                {entries.length > 1 && index > 0 && (
                                                                    <button
                                                                        type="button"
                                                                        className="remove-btn"
                                                                        onClick={() => removeDocumentField(document.documentName, entry.id)}
                                                                        title="Remove this document field"
                                                                    >
                                                                        ✕ Remove
                                                                    </button>
                                                                )}
                                                            </div>

                                                            <div className="upload-actions">

                                                                {/* Upload from Device */}
                                                                <label
                                                                    className="upload-btn"
                                                                    htmlFor={`file-input-${entry.id}`}
                                                                >
                                                                    📁 Upload
                                                                    <input
                                                                        id={`file-input-${entry.id}`}
                                                                        type="file"
                                                                        accept=".pdf,.jpg,.jpeg,.png,.heic,.heif"
                                                                        hidden
                                                                        onChange={(e) => {
                                                                            const file = e.target.files[0];
                                                                            if (file) {
                                                                                handleFileChange(document.documentName, entry.id, file);
                                                                            }
                                                                            e.target.value = "";
                                                                        }}
                                                                    />
                                                                </label>

                                                                {/* Capture from Camera */}
                                                                <label
                                                                    className="capture-btn"
                                                                    htmlFor={`camera-input-${entry.id}`}
                                                                >
                                                                    📷 Capture
                                                                    <input
                                                                        id={`camera-input-${entry.id}`}
                                                                        type="file"
                                                                        accept="image/*"
                                                                        capture="environment"
                                                                        hidden
                                                                        onChange={(e) => {
                                                                            const file = e.target.files[0];
                                                                            if (file) {
                                                                                handleFileChange(document.documentName, entry.id, file);
                                                                            }
                                                                            e.target.value = "";
                                                                        }}
                                                                    />
                                                                </label>

                                                            </div>

                                                            {entry.file && (
                                                                <div className="selected-file">
                                                                    ✅ {entry.file.name} ({(entry.file.size / 1024).toFixed(1)} KB)

                                                                    {entry.previewUrl && (
                                                                        <img
                                                                            src={entry.previewUrl}
                                                                            alt="Preview"
                                                                            className="document-preview"
                                                                        />
                                                                    )}
                                                                </div>
                                                            )}

                                                            {entry.error && (
                                                                <div className="document-field-error">
                                                                    ⚠️ {entry.error}
                                                                </div>
                                                            )}
                                                        </div>
                                                    );
                                                })}
                                            </div>

                                            <div className="add-document-container">
                                                <button
                                                    type="button"
                                                    className="add-another-btn"
                                                    onClick={() => addDocumentField(document.documentName)}
                                                >
                                                    + Add Another {document.documentName}
                                                </button>
                                            </div>
                                        </div>
                                    );
                                })}

                            </div>

                            <div className="application-actions">

                                <button
                                    className="submit-request-button"
                                    onClick={submitRequest}
                                    disabled={isSubmitting}
                                >
                                    {isSubmitting ? "Submitting Application..." : "Submit Request →"}
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
