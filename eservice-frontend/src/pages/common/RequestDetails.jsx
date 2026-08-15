import { useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import axios from "axios";
import { API_URL } from "../../config";
import "./RequestDetails.css";

function RequestDetails() {

    const { id } = useParams();

    const [request,
        setRequest] =
        useState(null);


    const [documents, setDocuments] =
        useState([]);
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

        axios.get(
            `${API_URL}/requests/${id}`,
            {
                headers: {
                    Authorization:
                        `Bearer ${token}`
                }
            }
        )
            .then(res => {
                setRequest(res.data);
            });

        axios.get(
            `${API_URL}/service-form-responses/request/${id}`,
            {
                headers: {
                    Authorization: `Bearer ${token}`
                }
            }
        )
            .then(res => {
                setFormResponses(res.data);
            });
        Promise.all([
            axios.get(`${API_URL}/documents/request/${id}`, {
                headers: { Authorization: `Bearer ${token}` }
            }),
            axios.get(`${API_URL}/documents/request/${id}/results`, {
                headers: { Authorization: `Bearer ${token}` }
            })
        ])
            .then(([customerRes, resultRes]) => {

                setDocuments([
                    ...customerRes.data,
                    ...resultRes.data
                ]);

            });

    }, [id]);

    if (!request) {

        return <h3>Loading...</h3>;

    }
    console.log("All documents:", documents);
    console.log("Customer docs:", customerDocuments);
    console.log("Result docs:", resultDocuments);
    const downloadDocument = async (documentId) => {
        try {
            const token = localStorage.getItem("token");

            const response = await axios.get(
                `${API_URL}/documents/download/${documentId}`,
                {
                    headers: {
                        Authorization: `Bearer ${token}`
                    },
                    responseType: "blob"
                }
            );

            const url = window.URL.createObjectURL(
                new Blob([response.data])
            );

            window.open(url, "_blank");

            setTimeout(() => {
                window.URL.revokeObjectURL(url);
            }, 1000);

        } catch (error) {
            console.error("Document download failed:", error);
        }
    };
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

                                <div className="documents-grid">

                                    {customerDocuments.map(doc => (

                                        <a
                                            key={doc.id}
                                            href={`http://localhost:8080/documents/download/${doc.id}`}
                                            target="_blank"
                                            rel="noreferrer"
                                            className="document-card"
                                        >

                                            <div className="document-info">

                    <span className="document-icon">
                        📄
                    </span>

                                                <span className="document-name">

                        {doc.documentName.length > 25
                            ? doc.documentName.substring(0, 25) + "..."
                            : doc.documentName}

                    </span>

                                            </div>

                                            <span className="download-icon">
                    ⬇
                </span>

                                        </a>

                                    ))}

                                </div>

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

                                                    <a
                                                        key={doc.id}
                                                        href={`http://localhost:8080/documents/download/${doc.id}`}
                                                        target="_blank"
                                                        rel="noreferrer"
                                                        className="result-document-card"
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

                                                    </a>

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

        </div>

    );

}

export default RequestDetails;
