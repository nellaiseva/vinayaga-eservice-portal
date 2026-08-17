import { useEffect, useState } from "react";
import Navbar from "../../components/Navbar";
import axios from "axios";
import {Link} from "react-router-dom";
import Pagination from "../../components/Pagination";
import { useNavigate } from "react-router-dom";import { API_URL } from "../../config";
import "./EmployeeDashboard.css"

import LoadingScreen from "../../components/LoadingScreen";
function EmployeeDashboard() {
    const [tasks, setTasks] = useState([]);
   // const [documents, setDocuments] = useState({});
    const navigate = useNavigate();
    const [selectedTask, setSelectedTask] = useState(null);

    const [resultFile, setResultFile] = useState(null);
    const [paymentTask, setPaymentTask] = useState(null);
    const [paymentAmount, setPaymentAmount] = useState("");
    const [paymentStatus, setPaymentStatus] = useState("UNPAID");

    const [showNotifyModal, setShowNotifyModal] = useState(false);

    const [completedTask, setCompletedTask] = useState(null);
    const [page, setPage] = useState(0);

    const [size, setSize] = useState(10);

    const [totalPages, setTotalPages] = useState(0);

    const [totalElements, setTotalElements] = useState(0);

    const [searchName, setSearchName] = useState("");

    const [searchPhone, setSearchPhone] = useState("");

    const [statusFilter, setStatusFilter] = useState("ALL");

    const [debouncedSearchName, setDebouncedSearchName] = useState("");

    const [debouncedSearchPhone, setDebouncedSearchPhone] = useState("");

    const [loading, setLoading] = useState(false);
    const [stats, setStats] = useState({

        pendingTasks: 0,

        assignedTasks: 0,

        inProgressTasks: 0,

        completedTasks: 0

    });
    useEffect(() => {

        const timer = setTimeout(() => {

            setDebouncedSearchName(searchName);

        }, 500);

        return () => clearTimeout(timer);

    }, [searchName]);
    useEffect(() => {

        const timer = setTimeout(() => {

            setDebouncedSearchPhone(searchPhone);

        }, 400);

        return () => clearTimeout(timer);

    }, [searchPhone]);
    const loadTasks = async () => {

        try {

            const token = localStorage.getItem("token");

            const employeeId =

                localStorage.getItem("employeeId");

            const params = new URLSearchParams();

            params.append("page", String(page));
            params.append("size", String(size));

            if (debouncedSearchName.trim()) {

                params.append(

                    "search",

                    debouncedSearchName.trim()

                );

            }

            if (debouncedSearchPhone.trim()) {

                params.append(

                    "phone",

                    debouncedSearchPhone.trim()

                );

            }

            if (statusFilter !== "ALL") {

                params.append(

                    "status",

                    statusFilter

                );

            }

            setLoading(true);

            const response = await axios.get(

                `${API_URL}/employee/tasks/${employeeId}?${params.toString()}`,

                {

                    headers: {

                        Authorization:

                            `Bearer ${token}`

                    }

                }

            );
            setTasks(response.data.content ?? []);
            setTotalPages(response.data.totalPages);

            setTotalElements(response.data.totalElements);

        }

        catch (err) {

            console.error(err);

        }

        finally {

            setLoading(false);

        }

    };
    const loadStats = async () => {

        try {

            const token =
                localStorage.getItem("token");

            const response = await axios.get(

                `${API_URL}/employee/tasks/dashboard/stats`,

                {
                    headers: {
                        Authorization: `Bearer ${token}`
                    }
                }

            );

            setStats(response.data);

        }

        catch (err) {

            console.error(err);

        }

    };
    const acceptTask = async (id) => {

        const token = localStorage.getItem("token");
        try {
            await axios.post(
                `${API_URL}/employee/tasks/${id}/accept`,
                {},
                {
                    headers: {
                        Authorization: `Bearer ${token}`
                    }
                }
            );
            await loadTasks();

            await loadStats();

        }catch(err){

            console.error(err);

            alert("Unable to accept task.");

        }
    };

    const uploadResultAndComplete = async () => {

        if (!resultFile) {

            alert(
                "Please select a document"
            );

            return;
        }

        const token =
            localStorage.getItem("token");

        const formData =
            new FormData();

        formData.append("file",resultFile);

        //formData.append(
          //  "taskId",
            //selectedTask.id
        //);
        formData.append(
            "requestId",
            String(selectedTask.request.id)
        );

        formData.append(
            "isResult",
            "true"
        );
        await axios.post(
            `${API_URL}/documents/upload`,
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

        await axios.post(

            `${API_URL}/employee/tasks/${selectedTask.id}/complete`,

            {},

            {

                headers: {

                    Authorization:
                        `Bearer ${token}`

                }

            }

        );
        await loadTasks();

        await loadStats();
        setCompletedTask(selectedTask);

        setSelectedTask(null);

        setResultFile(null);

        setShowNotifyModal(true);

        console.log(selectedTask);
    };const savePayment = async () => {

        if (
            paymentStatus === "PAID"
            &&
            paymentAmount === ""
        ) {

            alert(
                "Enter amount"
            );

            return;
        }

        const token =
            localStorage.getItem("token");

        await axios.post(

            `${API_URL}/requests/${paymentTask.request.id}/payment`,

            null,

            {

                params: {

                    status:
                    paymentStatus,

                    amount:
                        paymentAmount === ""
                            ? 0
                            : Number(paymentAmount)

                },

                headers: {

                    Authorization:
                        `Bearer ${token}`

                }

            }

        );
        await loadTasks();

        await loadStats();

        alert(
            "Payment Updated"
        );

        setPaymentTask(null);

        loadTasks();
    };
    const getWhatsappMessage = () => {

        return `🎉 Vinayaga E-Service

Dear ${completedTask.request.customerName},

Your request for "${completedTask?.request?.service?.serviceName}" has been completed successfully.

Please login to download your result.

${window.location.origin}/customer-login

Thank you.`;

    };
    const openWhatsApp = () => {

        const phone = completedTask.request.phoneNumber
            .replace(/\D/g, "");

        const fullPhone = phone.startsWith("91")
            ? phone
            : `91${phone}`;

        const message = encodeURIComponent(getWhatsappMessage());

        window.open(
            `https://web.whatsapp.com/send?phone=${fullPhone}&text=${message}`,
            "_blank"
        );
    };

    const copyMessage = async () => {

        await navigator.clipboard.writeText(
            getWhatsappMessage()
        );

        alert("Message copied.");

    };

    const closeNotifyModal = () => {

        setShowNotifyModal(false);

        setCompletedTask(null);

        void loadTasks();
    };
    useEffect(() => {

        void loadTasks();


    }, [

        page,

        size,

        debouncedSearchName,

        debouncedSearchPhone,

        statusFilter

    ]);
    useEffect(() => {

        setPage(0);

    }, [

        debouncedSearchName,

        debouncedSearchPhone,

        statusFilter

    ]);
    useEffect(() => {

        void loadStats();

    }, []);
    if (loading) {
        return <LoadingScreen message="Loading dashboard..." />;
    }
    return (
        <>
            <Navbar />

            <div className="page-bg">

                <div className="employee-dashboard-page">

                    <div className="employee-dashboard-container">

                        {/* Header */}

                        <div className="employee-dashboard-header">

                            <h1 className="employee-dashboard-title">

                                My Tasks

                            </h1>

                            <p className="employee-dashboard-subtitle">

                                Process assigned requests and update their status

                            </p>

                        </div>

                        {/* Stats Cards */}

                        <div className="employee-stats-grid">

                            <div className="employee-stat-card">

                                <div className="employee-stat-value">

                                    {stats.pendingTasks}

                                </div>

                                <div className="employee-stat-label">

                                    Pending Tasks

                                </div>

                            </div>

                            <div className="employee-stat-card">

                                <div className="employee-stat-value employee-assigned-value">

                                    {stats.assignedTasks}

                                </div>

                                <div className="employee-stat-label">

                                    Assigned Tasks

                                </div>

                            </div>

                            <div className="employee-stat-card">

                                <div className="employee-stat-value employee-progress-value">

                                    {stats.inProgressTasks}

                                </div>

                                <div className="employee-stat-label">

                                    In Progress

                                </div>

                            </div>

                            <div className="employee-stat-card">

                                <div className="employee-stat-value employee-completed-value">

                                    {stats.completedTasks}

                                </div>

                                <div className="employee-stat-label">

                                    Completed

                                </div>

                            </div>

                        </div>

                        {/* Filters */}

                        <div className="employee-filter-grid">

                            <input
                                type="text"
                                placeholder="Search Customer"
                                value={searchName}
                                onChange={(e) => setSearchName(e.target.value)}
                                className="employee-filter-input"
                            />

                            <input
                                type="text"
                                placeholder="Search Phone"
                                value={searchPhone}
                                onChange={(e) => setSearchPhone(e.target.value)}
                                className="employee-filter-input"
                            />

                            <select
                                value={statusFilter}
                                onChange={(e) => setStatusFilter(e.target.value)}
                                className="employee-filter-select"
                            >

                                <option value="ALL">

                                    All Status

                                </option>

                                <option value="PENDING">

                                    Pending

                                </option>

                                <option value="ACCEPTED">

                                    Accepted

                                </option>

                                <option value="IN_PROGRESS">

                                    In Progress

                                </option>

                                <option value="COMPLETED">

                                    Completed

                                </option>

                            </select>

                        </div>
                        {/* Table */}

                        <div className="employee-table-card">

                            <table className="employee-table">

                                <thead className="employee-table-head">

                                <tr>

                                    <th className="employee-table-heading">
                                        ID
                                    </th>

                                    <th className="employee-table-heading">
                                        Date
                                    </th>

                                    <th className="employee-table-heading">
                                        Customer
                                    </th>

                                    <th className="employee-table-heading">
                                        Service
                                    </th>

                                    <th className="employee-table-heading">
                                        Documents
                                    </th>

                                    <th className="employee-table-heading">
                                        Status
                                    </th>

                                    <th className="employee-table-heading">
                                        Amount
                                    </th>

                                    <th className="employee-table-heading">
                                        Payment
                                    </th>

                                    <th className="employee-table-heading">
                                        Action
                                    </th>

                                </tr>

                                </thead>

                                <tbody>

                                {loading && (

                                    <tr>

                                        <td
                                            colSpan="9"
                                            className="employee-empty-state"
                                        >

                                            Loading...

                                        </td>

                                    </tr>

                                )}

                                {!loading && tasks.length === 0 && (

                                    <tr>

                                        <td
                                            colSpan="9"
                                            className="employee-empty-state"
                                        >

                                            No tasks found.

                                        </td>

                                    </tr>

                                )}

                                {tasks.map(task => (

                                    <tr
                                        key={task.id}
                                        className="employee-table-row"
                                    >

                                        <td className="employee-table-cell employee-id-cell">

                                            <Link
                                                to={`/request-details/${task.request.id}`}
                                                className="employee-request-link"
                                            >

                                                #{task.request.id}

                                            </Link>

                                        </td>

                                        <td className="employee-table-cell employee-date-cell">

                                            {

                                                new Date(

                                                    task.request.createdAt

                                                ).toLocaleString(

                                                    "en-IN",

                                                    {

                                                        day:"2-digit",

                                                        month:"short",

                                                        year:"numeric",

                                                        hour:"numeric",

                                                        minute:"2-digit",

                                                        hour12:true

                                                    }

                                                )

                                            }

                                        </td>

                                        <td className="employee-table-cell employee-name-cell">

                                            {task.request.customerName}

                                        </td>

                                        <td className="employee-table-cell">

                                            {task.request?.service?.serviceName}

                                        </td>

                                        <td className="employee-table-cell">

                                            <button

                                                onClick={()=>

                                                    navigate(

                                                        `/request-details/${task.request.id}`

                                                    )

                                                }

                                                className="employee-view-button"

                                            >

                                                View

                                            </button>

                                        </td>

                                        <td className="employee-table-cell">

                    <span

                        className={`employee-status-badge

                        ${

                            task.status==="COMPLETED"

                                ?

                                "employee-status-completed"

                                :

                                task.status==="ACCEPTED"

                                    ?

                                    "employee-status-accepted"

                                    :

                                    "employee-status-pending"

                        }

                        `}

                    >

                        {task.status}

                    </span>

                                        </td>

                                        <td className="employee-table-cell">

                                            ₹ {(task.request.amount ?? 0).toLocaleString("en-IN")}

                                        </td>

                                        <td className="employee-table-cell">

                                            {

                                                task.request.paymentStatus==="PAID"

                                                    ?

                                                    (

                                                        <span className="employee-payment-paid">

                                    PAID

                                </span>

                                                    )

                                                    :

                                                    (

                                                        <span className="employee-payment-unpaid">

                                    UNPAID

                                </span>

                                                    )

                                            }

                                        </td>

                                        <td className="employee-action-cell">

                                            <div className="employee-action-group">

                                                {task.status==="PENDING" && (

                                                    <button

                                                        onClick={()=>

                                                            acceptTask(task.id)

                                                        }

                                                        className="employee-accept-button"

                                                    >

                                                        Accept

                                                    </button>

                                                )}

                                                {task.status==="ACCEPTED"

                                                    &&

                                                    task.request.paymentStatus!=="PAID"

                                                    &&

                                                    (

                                                        <button

                                                            onClick={()=>{

                                                                setPaymentTask(task);

                                                                setPaymentAmount(

                                                                    task.request.amount ?? ""

                                                                );

                                                                setPaymentStatus(

                                                                    task.request.paymentStatus ?? "UNPAID"

                                                                );

                                                            }}

                                                            className="employee-payment-button"

                                                        >

                                                            Payment

                                                        </button>

                                                    )

                                                }

                                                {task.status==="ACCEPTED"

                                                    &&

                                                    task.request.paymentStatus==="PAID"

                                                    &&

                                                    (

                                                        <button

                                                            onClick={()=>

                                                                setSelectedTask(task)

                                                            }

                                                            className="employee-complete-button"

                                                        >

                                                            Complete

                                                        </button>

                                                    )

                                                }

                                                {task.status==="COMPLETED" && (

                                                    <button

                                                        disabled

                                                        className="employee-completed-button"

                                                    >

                                                        Completed

                                                    </button>

                                                )}

                                            </div>

                                        </td>

                                    </tr>

                                ))}

                                </tbody>

                            </table>

                            <Pagination

                                page={page}

                                totalPages={totalPages}

                                size={size}

                                setPage={setPage}

                                setSize={setSize}

                                totalElements={totalElements}

                            />

                        </div>
                        {
                            selectedTask && (

                                <div className="employee-modal-overlay">

                                    <div className="employee-upload-modal">

                                        <h3 className="employee-modal-title">

                                            Upload Result Document

                                        </h3>

                                        <div className="employee-info-card">

                                            <p className="employee-info-heading">

                                                Customer

                                            </p>

                                            <p className="employee-info-value">

                                                {selectedTask?.request.customerName}

                                            </p>

                                            <hr className="employee-divider"/>

                                            <p className="employee-info-heading">

                                                Service

                                            </p>

                                            <p className="employee-info-value">

                                                {selectedTask?.request.service?.serviceName}

                                            </p>

                                        </div>

                                        <div className="employee-upload-group">

                                            <label className="employee-upload-label">

                                                Result Document

                                            </label>

                                            <input

                                                type="file"

                                                className="employee-file-input"

                                                onChange={(e)=>

                                                    setResultFile(

                                                        e.target.files?.[0] || null

                                                    )

                                                }

                                            />

                                        </div>

                                        <div className="employee-modal-actions">

                                            <button

                                                onClick={()=>{

                                                    setSelectedTask(null);

                                                    setResultFile(null);

                                                }}

                                                className="employee-cancel-button"

                                            >

                                                Cancel

                                            </button>

                                            <button

                                                onClick={uploadResultAndComplete}

                                                className="employee-upload-button"

                                            >

                                                Upload & Complete

                                            </button>

                                        </div>

                                    </div>

                                </div>

                            )
                        }
                        {
                            paymentTask && (

                                <div className="employee-modal-overlay">

                                    <div className="employee-payment-modal">

                                        <h2 className="employee-modal-title">

                                            Update Payment

                                        </h2>

                                        <div className="employee-form-group">

                                            <label className="employee-form-label">

                                                Amount (₹)

                                            </label>

                                            <input
                                                type="number"
                                                className="employee-form-input"
                                                placeholder="Enter Amount"
                                                value={paymentAmount}
                                                disabled={paymentStatus === "PAID"}
                                                onChange={(e) =>
                                                    setPaymentAmount(e.target.value)
                                                }
                                            />

                                        </div>

                                        <div className="employee-form-group">

                                            <label className="employee-form-label">

                                                Payment Status

                                            </label>

                                            <select
                                                className="employee-form-select"
                                                value={paymentStatus}
                                                disabled={paymentStatus === "PAID"}
                                                onChange={(e) =>
                                                    setPaymentStatus(e.target.value)
                                                }
                                            >

                                                <option value="UNPAID">

                                                    Unpaid

                                                </option>

                                                <option value="PAID">

                                                    Paid

                                                </option>

                                            </select>

                                        </div>

                                        <div className="employee-modal-actions">

                                            <button

                                                onClick={() =>
                                                    setPaymentTask(null)
                                                }

                                                className="employee-cancel-button"

                                            >

                                                Cancel

                                            </button>

                                            <button

                                                onClick={savePayment}

                                                className="employee-save-button"

                                            >

                                                Save Payment

                                            </button>

                                        </div>

                                    </div>

                                </div>

                            )
                        }
                        {
                            showNotifyModal && completedTask && (

                                <div className="employee-modal-overlay">

                                    <div className="employee-notification-modal">

                                        <div className="employee-notification-header">

                                            <div className="employee-success-icon">

                                                ✅

                                            </div>

                                            <h2 className="employee-notification-title">

                                                Request Completed

                                            </h2>

                                            <p className="employee-notification-subtitle">

                                                The result has been uploaded successfully.

                                            </p>

                                        </div>

                                        <div className="employee-summary-card">

                                            <p>

                        <span className="employee-summary-label">

                            Customer

                        </span>

                                                <br/>

                                                {completedTask.request.customerName}

                                            </p>

                                            <hr className="employee-divider"/>

                                            <p>

                        <span className="employee-summary-label">

                            Phone

                        </span>

                                                <br/>

                                                {completedTask.request.phoneNumber}

                                            </p>

                                            <hr className="employee-divider"/>

                                            <p>

                        <span className="employee-summary-label">

                            Service

                        </span>

                                                <br/>

                                                {completedTask?.request?.service?.serviceName}

                                            </p>

                                        </div>

                                        <div className="employee-notification-actions">

                                            <button

                                                onClick={openWhatsApp}

                                                className="employee-whatsapp-button"

                                            >

                                                📱 Open WhatsApp

                                            </button>

                                            <button

                                                onClick={copyMessage}

                                                className="employee-copy-button"

                                            >

                                                📋 Copy Message

                                            </button>

                                            <button

                                                onClick={closeNotifyModal}

                                                className="employee-close-button"

                                            >

                                                Close

                                            </button>

                                        </div>

                                    </div>

                                </div>

                            )
                        }

                    </div>

                </div>
            </div>
            </>

            );
    }

export default EmployeeDashboard;