import { useEffect, useState } from "react";
import axios from "axios";
import CustomerNavbar from "../../components/CustomerNavbar";
import {Link} from "react-router-dom";
import { API_URL } from "../../config";
import Pagination from "../../components/Pagination";
import "./Myrequest.css";
import LoadingScreen from "../../components/LoadingScreen";
function MyRequests() {

    const [requests, setRequests] = useState([]);
    const [documents,
        setDocuments] =
        useState({});
    const [showFeedback, setShowFeedback] = useState(false);

    const [selectedRequest, setSelectedRequest] = useState(null);

    const [rating, setRating] = useState(5);

    const [comment, setComment] = useState("");
    const [page,setPage]=useState(0);

    const [size,setSize]=useState(10);

    const [totalPages,setTotalPages]=useState(0);

    const [totalElements,setTotalElements]=useState(0);

    const [loading,setLoading]=useState(false);
    useEffect(() => {

        const token = localStorage.getItem("token");
        const phoneNumber =
            localStorage.getItem("customerPhone");

        setLoading(true);

        axios.get(

            `${API_URL}/requests/phone/${phoneNumber}?page=${page}&size=${size}`,

            {

                headers:{

                    Authorization:`Bearer ${token}`

                }

            }

        )

            .then(res=>{

                setRequests(

                    res.data.content

                );

                setTotalPages(

                    res.data.totalPages

                );

                setTotalElements(

                    res.data.totalElements

                );

            })


            .catch(console.error)

             .finally(()=>{

            setLoading(false);

        });

    }, [

        page,

        size

    ]);
    const loadDocuments = async (
        requestId
    ) => {

        const token =
            localStorage.getItem("token");

        const res = await axios.get(
            `${API_URL}/documents/request/${requestId}`,
            {
                headers: {
                    Authorization:
                        `Bearer ${token}`
                }
            }
        );

        setDocuments(prev => ({

            ...prev,

            [requestId]: res.data

        }));
    };
    const submitFeedback = async () => {

        try {

            await axios.post(
                `${API_URL}/feedback`,
                {
                    requestId: selectedRequest.id,
                    rating,
                    comment
                }
            );

            alert("Feedback submitted!");

            setRequests(prev =>

                prev.map(request =>

                    request.id === selectedRequest.id

                        ? {

                            ...request,

                            feedbackSubmitted: true

                        }

                        : request

                )

            );

            setShowFeedback(false);

            setRating(5);

            setComment("");

            setSelectedRequest(null);

        }

        catch (err) {

            alert(
                err.response?.data ||
                "Unable to submit feedback."
            );

        }

    };
    if (loading) {
        return <LoadingScreen message="Loading your requests..." />;
    }
    return (
        <>
            <CustomerNavbar />

            <div className="page-bg">

                <div className="requests-page">

                    <div className="requests-container">

                        <div className="requests-header">

                            <div>

                                <h1 className="requests-title">

                                    My Requests

                                </h1>

                                <p className="requests-subtitle">

                                    Track the status of your applications.

                                </p>

                            </div>

                            <Link
                                to="/customer-services"
                                className="new-request-button"
                            >

                                New Application

                            </Link>

                        </div>

                        <div className="requests-list">

                            {

                                requests.length === 0 ?

                                    (

                                        <div className="requests-empty">

                                            No requests found.

                                        </div>

                                    )

                                    :

                                    requests.map(request => (

                                        <div
                                            key={request.id}
                                            className="request-card"
                                        >

                                            <div className="request-card-header">

                                                <div>

                                                    <div className="request-title-row">

                                                    <span className="request-id">

                                                        #{request.id}

                                                    </span>

                                                        <h3 className="request-service-name">

                                                            {request.serviceName}

                                                        </h3>

                                                    </div>

                                                    <p className="request-date">

                                                        {

                                                            new Date(

                                                                request.createdAt

                                                            ).toLocaleString(

                                                                "en-IN",

                                                                {

                                                                    day: "2-digit",

                                                                    month: "short",

                                                                    year: "numeric",

                                                                    hour: "numeric",

                                                                    minute: "2-digit",

                                                                    hour12: true

                                                                }

                                                            )

                                                        }

                                                    </p>

                                                </div>

                                                <div className="request-actions">

                                                    {request.status === "PENDING" && (

                                                        <span className="status-badge pending-status">

                                                        📝 Submitted

                                                    </span>

                                                    )}

                                                    {request.status === "IN_PROGRESS" && (

                                                        <span className="status-badge progress-status">

                                                        ✅ Accepted

                                                    </span>

                                                    )}
                                                    {request.status === "COMPLETED" && (

                                                        <>

                                                            {!request.feedbackSubmitted && (

                                                                <button

                                                                    className="feedback-button"

                                                                    onClick={() => {

                                                                        setSelectedRequest(request);

                                                                        setShowFeedback(true);

                                                                    }}

                                                                >

                                                                    ⭐ Rate Service

                                                                </button>

                                                            )}

                                                            <span className="status-badge completed-status">

            🎉 Completed

        </span>

                                                        </>

                                                    )}

                                                    {request.status === "REJECTED" && (

                                                        <span className="status-badge rejected-status">

                                                        ❌ Rejected

                                                    </span>

                                                    )}

                                                    <Link

                                                        to={`/request-details/${request.id}`}

                                                        className="details-button"

                                                    >

                                                        Details →

                                                    </Link>

                                                </div>

                                            </div>

                                            <div className="request-documents">

                                                <button
                                                    className="documents-button"
                                                    onClick={() => {

                                                        if (documents[request.id]) {

                                                            setDocuments(prev => {

                                                                const updated = { ...prev };

                                                                delete updated[request.id];

                                                                return updated;

                                                            });

                                                        } else {

                                                            loadDocuments(request.id);

                                                        }

                                                    }}
                                                >

                                                    {documents[request.id] ? "Hide Documents" : "View Documents"}

                                                </button>
                                                {documents[request.id] && (

                                                    <div className="documents-list">

                                                        {

                                                            documents[request.id].map(doc => (

                                                                <a

                                                                    key={doc.id}

                                                                    href={`${API_URL}/documents/download/${doc.id}`}

                                                                    target="_blank"

                                                                    rel="noreferrer"

                                                                    className="document-item"

                                                                >

                                                                    📄 {doc.documentName || doc.fileName || "Document"}
                                                                </a>

                                                            ))

                                                        }

                                                    </div>

                                                )}

                                            </div>

                                        </div>

                                    ))

                            }

                        </div>

                    </div>

                    {

                        showFeedback &&

                        <div className="feedback-overlay">

                            <div className="feedback-modal">

                                <h2 className="feedback-title">

                                    Rate Service

                                </h2>

                                <div className="feedback-stars">

                                    {

                                        [1,2,3,4,5].map(star => (

                                            <button

                                                key={star}

                                                onClick={() =>

                                                    setRating(star)

                                                }

                                                className="star-button"

                                            >

                                                {

                                                    star <= rating

                                                        ? "⭐"

                                                        : "☆"

                                                }

                                            </button>

                                        ))

                                    }

                                </div>

                                <textarea

                                    value={comment}

                                    onChange={(e) =>

                                        setComment(e.target.value)

                                    }

                                    rows={5}

                                    placeholder="Write your feedback..."

                                    className="feedback-textarea"

                                />

                                <div className="feedback-actions">

                                    <button

                                        onClick={() => {

                                            setShowFeedback(false);

                                            setComment("");

                                            setRating(5);

                                            setSelectedRequest(null);

                                        }}

                                        className="feedback-cancel"

                                    >

                                        Cancel

                                    </button>

                                    <button

                                        disabled={comment.trim() === ""}

                                        onClick={submitFeedback}

                                        className="feedback-submit"

                                    >

                                        Submit

                                    </button>

                                </div>

                            </div>

                        </div>

                    }

                    <div className="requests-pagination">

                        <Pagination

                            page={page}

                            totalPages={totalPages}

                            totalElements={totalElements}

                            pageSize={size}

                            onPageChange={setPage}

                            onPageSizeChange={(newSize) => {

                                setSize(newSize);

                                setPage(0);

                            }}

                        />

                    </div>

                </div>

            </div>

        </>
    );
}

export default MyRequests;