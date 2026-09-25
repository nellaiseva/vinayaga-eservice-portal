import { useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import axios from "axios";
import { API_URL } from "../../config";
import "./RequestDetails.css";
import LoadingScreen from "../../components/LoadingScreen";
import DeleteConfirmationModal from "../../components/DeleteConfirmationModal";
function RequestDetails() {

    const { id } = useParams();

    const [request,
        setRequest] =
        useState(null);

    const [loading, setLoading] = useState(true);

    const [documents, setDocuments] =
        useState([]);
    const [documentToDelete, setDocumentToDelete] = useState(null);
    const [isDeleting, setIsDeleting] = useState(false);
    const [deleteError, setDeleteError] = useState("");
    const customerDocuments = (documents || []).filter(
        doc => !doc.resultDocument
    );

    const resultDocuments = (documents || []).filter(
        doc => doc.resultDocument
    );

    const [formResponses,
        setFormResponses] =
        useState([]);
    useEffect(() => {

        const token =
            localStorage.getItem("token");

        const loadData = async () => {

            try {

                setLoading(true);

                const [
                    requestRes,
                    formResponse,
                    customerRes,
                    resultRes
                ] = await Promise.all([

                    axios.get(
                        `${API_URL}/requests/${id}`,
                        {
                            headers: {
                                Authorization:
                                    `Bearer ${token}`
                            }
                        }
                    ),

                    axios.get(
                        `${API_URL}/service-form-responses/request/${id}`,
                        {
                            headers: {
                                Authorization:
                                    `Bearer ${token}`
                            }
                        }
                    ),

                    axios.get(
                        `${API_URL}/documents/request/${id}`,
                        {
                            headers: {
                                Authorization:
                                    `Bearer ${token}`
                            }
                        }
                    ),

                    axios.get(
                        `${API_URL}/documents/request/${id}/results`,
                        {
                            headers: {
                                Authorization:
                                    `Bearer ${token}`
                            }
                        }
                    )

                ]);

                setRequest(requestRes.data);
                setFormResponses(formResponse.data);

                setDocuments([
                    ...customerRes.data,
                    ...resultRes.data
                ]);

            } catch (error) {

                console.error(error);

            } finally {

                setLoading(false);

            }

        };

        loadData();

    }, [id]);
    console.log("All documents:", documents);
    console.log("Customer docs:", customerDocuments);
    console.log("Result docs:", resultDocuments);
    const downloadDocument = async (documentId, fileName) => {

        try {

            const token =
                localStorage.getItem("token");

            const response = await axios.get(
                `${API_URL}/documents/download/${documentId}`,
                {
                    headers: {
                        Authorization:
                            `Bearer ${token}`
                    },
                    responseType: "blob"
                }
            );

            const blob = new Blob(
                [response.data],
                {
                    type:
                        response.headers["content-type"] ||
                        "application/octet-stream"
                }
            );

            const url =
                window.URL.createObjectURL(blob);

            const link =
                document.createElement("a");

            link.href = url;

            link.download =
                fileName || "document";

            document.body.appendChild(link);

            link.click();

            link.remove();

            window.URL.revokeObjectURL(url);

        } catch (error) {

            console.error(
                "Document download failed:",
                error
            );

            alert(
                "Failed to download document."
            );
        }
    };

    const loggedInPhone = localStorage.getItem("customerPhone") || localStorage.getItem("phoneNumber");
    const userRole = localStorage.getItem("role");
    const canDeleteCustomerDocs = (userRole === "OWNER") || (Boolean(loggedInPhone && request && loggedInPhone === request.phoneNumber));

    const handleDeleteClick = (doc) => {
        setDeleteError("");
        setDocumentToDelete(doc);
    };

    const handleConfirmDelete = async () => {
        if (!documentToDelete) return;
        try {
            setIsDeleting(true);
            setDeleteError("");
            const token = localStorage.getItem("token");
            await axios.delete(`${API_URL}/documents/${documentToDelete.id}`, {
                headers: {
                    Authorization: `Bearer ${token}`
                }
            });
            setDocuments(prev => prev.filter(d => d.id !== documentToDelete.id));
            setDocumentToDelete(null);
        } catch (err) {
            console.error("Failed to delete document:", err);
            setDeleteError(
                err.response?.data?.message ||
                (typeof err.response?.data === "string" ? err.response?.data : null) ||
                "Failed to delete document. Please try again."
            );
        } finally {
            setIsDeleting(false);
        }
    };

    const handleCancelDelete = () => {
        if (!isDeleting) {
            setDocumentToDelete(null);
            setDeleteError("");
        }
    };

    if (loading) {
        return <LoadingScreen message="Loading request details..." />;
    }

    if (!request) {
        return (
            <div className="page-bg">
                <div className="request-details-page">
                    <div className="request-details-container">
                        <div className="glass-card">
                            <h2>Request Not Found</h2>
                            <p>
                                The requested application could not be found.
                            </p>
                        </div>
                    </div>
                </div>
            </div>
        );
    }

    return (

        <div className="page-bg">

            <div className="request-details-page">

                <div className="request-details-container">

                    {/* Header */}

                    <div className="request-header">

                        <div>

                            <p className="request-id">

                                Request #{request.id}

                            </p>

                            <h1 className="request-title">

                                {request.service?.serviceName}

                            </h1>

                        </div>

                        <span
                            className={`request-status ${
                                request.status === "PENDING"
                                    ? "status-pending"
                                    : request.status === "ASSIGNED"
                                        ? "status-assigned"
                                        : request.status === "IN_PROGRESS"
                                            ? "status-progress"
                                            : "status-completed"
                            }`}
                        >

                        {request.status?.replace("_", " ")}

                    </span>

                    </div>

                    {/* Main Layout */}

                    <div className="request-layout">

                        {/* Left Section */}

                        <div className="request-left-column">

                            <div className="glass-card">
                                {/* Applicant */}

                                <h3 className="section-title">
                                    Applicant
                                </h3>

                                <div className="applicant-grid">

                                    <div className="info-card">

                                        <p className="info-label">
                                            Customer
                                        </p>

                                        <p className="info-value">
                                            {request.customerName}
                                        </p>

                                    </div>

                                    <div className="info-card">

                                        <p className="info-label">
                                            Phone
                                        </p>

                                        <p className="info-value">
                                            {request.phoneNumber}
                                        </p>

                                    </div>

                                </div>

                                <h4 className="subsection-title">
                                    Application Details
                                </h4>

                                <div className="details-list">

                                    {formResponses.map(response => (

                                        <div
                                            key={response.fieldName}
                                            className="detail-row"
                                        >

                <span className="detail-label">
                    {response.fieldName}
                </span>

                                            <span className="detail-value">
                    {response.value || "-"}
                </span>

                                        </div>

                                    ))}

                                </div>

                            </div>

                            <div className="glass-card">

                                <h3 className="section-title">
                                    Uploaded Documents
                                </h3>

                                {customerDocuments.length === 0 ? (
                                    <p className="no-documents-message">No uploaded documents available.</p>
                                ) : (
                                    <div className="documents-grid">

                                        {customerDocuments.map(doc => (

                                            <div
                                                key={doc.id}
                                                className="document-card"
                                            >
                                                <div
                                                    className="document-info clickable"
                                                    onClick={() =>
                                                        downloadDocument(
                                                            doc.id,
                                                            doc.fileName
                                                        )
                                                    }
                                                    title={`Download ${doc.documentName}`}
                                                    role="button"
                                                    tabIndex={0}
                                                    onKeyDown={(e) => {
                                                        if (e.key === "Enter" || e.key === " ") {
                                                            downloadDocument(doc.id, doc.fileName);
                                                        }
                                                    }}
                                                >
                                                    <span className="document-icon">
                                                        📄
                                                    </span>

                                                    <span className="document-name">
                                                        {doc.documentName.length > 25
                                                            ? doc.documentName.substring(0, 25) + "..."
                                                            : doc.documentName}
                                                    </span>
                                                </div>

                                                <div className="document-actions">
                                                    <button
                                                        type="button"
                                                        onClick={() =>
                                                            downloadDocument(
                                                                doc.id,
                                                                doc.fileName
                                                            )
                                                        }
                                                        className="download-icon"
                                                        title="Download document"
                                                        aria-label={`Download ${doc.documentName}`}
                                                    >
                                                        ⬇
                                                    </button>

                                                    {canDeleteCustomerDocs && (
                                                        <button
                                                            type="button"
                                                            onClick={(e) => {
                                                                e.stopPropagation();
                                                                handleDeleteClick(doc);
                                                            }}
                                                            className="delete-doc-btn"
                                                            title="Delete document"
                                                            aria-label={`Delete ${doc.documentName}`}
                                                        >
                                                            🗑️
                                                        </button>
                                                    )}
                                                </div>
                                            </div>
                                        ))}

                                    </div>
                                )}

                            </div>

                            {/* Result Documents */}

                            {

                                resultDocuments.length > 0 && (

                                    <div className="glass-card">

                                        <h3 className="section-title">

                                            Result Documents

                                        </h3>

                                        <div className="documents-grid">

                                            {

                                                resultDocuments.map(doc => (

                                                    <button
                                                        key={doc.id}
                                                        onClick={() =>
                                                            downloadDocument(
                                                                doc.id,
                                                                doc.fileName
                                                            )
                                                        }                                                        className="result-document-card"
                                                    >
                                                        <div className="document-info">
        <span className="document-icon">
            📜
        </span>

                                                            <span className="document-name">
            {doc.documentName}
        </span>
                                                        </div>

                                                        <span className="download-icon">
        ⬇
    </span>
                                                    </button>
                                                ))

                                            }

                                        </div>

                                    </div>

                                )

                            }

                        </div>

                        {/* Timeline */}

                        <div className="timeline-card">

                            <h3 className="section-title">

                                Status Timeline

                            </h3>

                            <div className="timeline-list">

                                <div className="timeline-item">

                                    <div className="timeline-dot submitted-dot"></div>

                                    <div>

                                        <p className="timeline-title">

                                            Submitted

                                        </p>

                                        <p className="timeline-text">

                                            Request Created

                                        </p>

                                    </div>

                                </div>

                                <div className="timeline-item">

                                    <div className="timeline-dot assigned-dot"></div>

                                    <div>

                                        <p className="timeline-title">

                                            Assigned

                                        </p>

                                        <p className="timeline-text">

                                            Employee Assigned

                                        </p>

                                    </div>

                                </div>

                                <div className="timeline-item">

                                    <div className="timeline-dot progress-dot"></div>

                                    <div>

                                        <p className="timeline-title">

                                            In Progress

                                        </p>

                                        <p className="timeline-text">

                                            Processing Request

                                        </p>

                                    </div>

                                </div>

                                <div className="timeline-item">

                                    <div className="timeline-dot completed-dot"></div>

                                    <div>

                                        <p className="timeline-title">

                                            Completed

                                        </p>

                                        <p className="timeline-text">

                                            Final Status

                                        </p>

                                    </div>

                                </div>

                            </div>

                        </div>

                    </div>

                </div>

            </div>

            <DeleteConfirmationModal
                isOpen={Boolean(documentToDelete)}
                documentName={documentToDelete?.documentName || documentToDelete?.fileName}
                isDeleting={isDeleting}
                errorMessage={deleteError}
                onConfirm={handleConfirmDelete}
                onCancel={handleCancelDelete}
            />

        </div>

    );

}

export default RequestDetails;
