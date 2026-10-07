import { useEffect, useState } from "react";
import axios from "axios";
import Navbar from "../../components/Navbar";
import { API_URL } from "../../config";
import Pagination from "../../components/Pagination";
import DashboardLayout from "../../layouts/EmployeeLayout.jsx";
import  "./EmployeeRequests.css"
import EmployeeLayout from "../../layouts/EmployeeLayout.jsx";
function EmployeeRequests() {

    const [requests, setRequests] = useState([]);
    const [searchName, setSearchName] = useState("");
    const [searchPhone, setSearchPhone] = useState("");
    const [debouncedSearchName, setDebouncedSearchName] =

        useState("");

    const [debouncedSearchPhone, setDebouncedSearchPhone] =

        useState("");
    const [statusFilter, setStatusFilter] = useState("ALL");
    const [page, setPage] = useState(0);

    const [size, setSize] = useState(10);

    const [totalPages, setTotalPages] = useState(0);

    const [totalElements, setTotalElements] = useState(0);

    const [loading, setLoading] = useState(false);
    const [stats, setStats] = useState({

        totalRequests: 0,

        pendingRequests: 0,

        assignedRequests: 0,

        inProgressRequests: 0,

        completedRequests: 0

    });
    useEffect(() => {
        loadRequests();
        const token =
            localStorage.getItem("token");

        axios.get(

            `${API_URL}/employee/requests/stats`,

            {

                headers: {

                    Authorization:
                        `Bearer ${token}`

                }

            }

        )

            .then(res => {

                setStats(res.data);

            });
    }, [

        page,

        size,

        debouncedSearchName,

        debouncedSearchPhone,

        statusFilter

    ]);
    useEffect(() => {

        const timer = setTimeout(() => {

            setDebouncedSearchName(searchName);

            setDebouncedSearchPhone(searchPhone);

        }, 400);

        return () => clearTimeout(timer);

    }, [

        searchName,

        searchPhone

    ]);
    const loadRequests = async () => {

        try {

            const token =
                localStorage.getItem("token");

            const params =
                new URLSearchParams();

            params.append("page", page);

            params.append("size", size);

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

            const response =

                await axios.get(

                    `${API_URL}/employee/requests?${params.toString()}`,

                    {
                        headers: {
                            Authorization:
                                `Bearer ${token}`
                        }
                    }

                );

            setRequests(
                response.data.content
            );

            setTotalPages(
                response.data.totalPages
            );

            setTotalElements(
                response.data.totalElements
            );

        }

        catch (error) {

            console.error(error);

        }

        finally {

            setLoading(false);

        }

    };

    const selfAssign = async (requestId) => {

        try {

            const token =
                localStorage.getItem("token");

            await axios.post(
                `${API_URL}/employee/tasks/${requestId}/self-assign`,
                {},
                {
                    headers: {
                        Authorization:
                            `Bearer ${token}`
                    }
                }
            );


            loadRequests();
            alert("Request assigned successfully");
        } catch (error) {

            alert(

                error.response?.data

                ||

                "Unable to self assign"

            );
            loadRequests();
        }
    };
    useEffect(() => {

        setPage(0);

    }, [

        searchName,

        searchPhone,

        statusFilter

    ]);
    if (loading) {

        return (

            <EmployeeLayout>

                <div className="employee-loading-page">

                    <div className="employee-loader"></div>

                </div>

            </EmployeeLayout>

        );

    }

    return (

        <>

            <Navbar />

            <div className="page-bg">

                <div className="employee-requests-page">

                    {/* Statistics */}

                    <div className="employee-stats-grid">

                        <div className="stats-card pending-card">

                            <p className="stats-label">

                                Pending Requests

                            </p>

                            <h3 className="stats-value">

                                {stats.pendingRequests}

                            </h3>

                        </div>

                        <div className="stats-card assigned-card">

                            <p className="stats-label">

                                Assigned

                            </p>

                            <h3 className="stats-value">

                                {stats.assignedRequests}

                            </h3>

                        </div>

                        <div className="stats-card progress-card">

                            <p className="stats-label">

                                In Progress

                            </p>

                            <h3 className="stats-value">

                                {stats.inProgressRequests}

                            </h3>

                        </div>

                        <div className="stats-card completed-card">

                            <p className="stats-label">

                                Completed

                            </p>

                            <h3 className="stats-value">

                                {stats.completedRequests}

                            </h3>

                        </div>

                    </div>

                    {/* Header */}

                    <div className="employee-requests-header">

                        <h1 className="employee-requests-title">

                            Available Requests

                        </h1>

                        <p className="employee-requests-subtitle">

                            Browse pending requests and assign them to yourself.

                        </p>

                    </div>

                    {/* Filters */}

                    <div className="employee-filters">

                        <input
                            type="text"
                            placeholder="Search Customer"
                            value={searchName}
                            onChange={(e)=>
                                setSearchName(e.target.value)
                            }
                            className="filter-input"
                        />

                        <input
                            type="text"
                            placeholder="Search Phone"
                            value={searchPhone}
                            onChange={(e)=>
                                setSearchPhone(e.target.value)
                            }
                            className="filter-input"
                        />

                        <select
                            value={statusFilter}
                            onChange={(e)=>
                                setStatusFilter(e.target.value)
                            }
                            className="filter-select"
                        >

                            <option value="ALL">All Status</option>

                            <option value="PENDING">Pending</option>

                            <option value="ASSIGNED">Assigned</option>

                            <option value="IN_PROGRESS">In Progress</option>

                            <option value="COMPLETED">Completed</option>

                        </select>

                    </div>

                    {/* Table */}

                    <div className="employee-requests-table-card">

                        <table className="employee-requests-table">

                            <thead className="employee-table-header">

                            <tr>

                                <th>ID</th>

                                <th>Customer</th>

                                <th>Phone</th>

                                <th>Service</th>

                                <th>Status</th>

                                <th>Action</th>

                            </tr>

                            </thead>

                            <tbody>

                            {

                                requests.length === 0 ?

                                    (

                                        <tr>

                                            <td
                                                colSpan="6"
                                                className="empty-row"
                                            >

                                                No requests found.

                                            </td>

                                        </tr>

                                    )

                                    :

                                    requests.map(request => (

                                        <tr
                                            key={request.id}
                                            className="request-row"
                                        >

                                            <td>

                                                #{request.id}

                                            </td>

                                            <td>

                                                {request.customerName}

                                            </td>

                                            <td>

                                                {request.phoneNumber}

                                            </td>

                                            <td>

                                                {request.serviceName}

                                            </td>

                                            <td>

                                                <span

                                                    className={`status-badge ${
                                                        request.status === "PENDING"

                                                            ?

                                                            "pending-status"

                                                            :

                                                            request.status === "ASSIGNED"

                                                                ?

                                                                "assigned-status"

                                                                :

                                                                request.status === "IN_PROGRESS"

                                                                    ?

                                                                    "progress-status"

                                                                    :

                                                                    request.status === "COMPLETED"

                                                                        ?

                                                                        "completed-status"

                                                                        :

                                                                        "default-status"

                                                    }`}

                                                >

                                                    {

                                                        request.status

                                                            ?

                                                            request.status.replaceAll("_", " ")

                                                            :

                                                            "-"

                                                    }

                                                </span>

                                            </td>

                                            <td className="action-cell">

                                                {

                                                    request.status === "PENDING"

                                                        ?

                                                        (

                                                            <button

                                                                className="self-assign-button"

                                                                onClick={() =>

                                                                    selfAssign(request.id)

                                                                }

                                                            >

                                                                Self Assign

                                                            </button>

                                                        )

                                                        :

                                                        (

                                                            <button

                                                                className="assigned-button"

                                                                disabled

                                                            >

                                                                Assigned To:

                                                                {" "}

                                                                {request.assignedEmployeeName}

                                                            </button>

                                                        )

                                                }

                                            </td>

                                        </tr>

                                    ))

                            }

                            </tbody>

                        </table>

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

export default EmployeeRequests;