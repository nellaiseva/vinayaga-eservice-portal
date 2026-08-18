import { useEffect, useState } from "react";
import axios from "axios";
import DashboardLayout
    from "../../layouts/DashboardLayout";
import { Link } from "react-router-dom";
import { API_URL } from "../../config";
import secureApi from "../../api/secureApi";
import {
    ResponsiveContainer,
    LineChart,
    Line,
    XAxis,
    YAxis,
    CartesianGrid,
    Tooltip
} from "recharts";

import "./Employees.css"
function Employees() {

    const [employees, setEmployees] =
        useState([]);
    const [dashboard, setDashboard] = useState(null);
    const [topEmployeeImageUrl, setTopEmployeeImageUrl] = useState("");
    const [searchName,
        setSearchName] =
        useState("");

    const [searchPhone,
        setSearchPhone] =
        useState("");
    useEffect(() => {

        const token =
            localStorage.getItem("token");

        axios.get(
            (`${API_URL}/employees`),
            {
                headers: {
                    Authorization:
                        `Bearer ${token}`
                }
            }
        )
            .then(res => {
                setEmployees(res.data);
            })
            .catch(err => {
                console.log(err);
            });
        axios.get(
            `${API_URL}/employees/dashboard`,
            {
                headers: {
                    Authorization: `Bearer ${token}`
                }
            }
        )
            .then(async res => {

                setDashboard(res.data);

                if (res.data.employeeOfMonth?.profileImage) {
                    const imageResponse = await secureApi.get(
                        `/employees/${res.data.employeeOfMonth.id}/profile-image`,
                        { responseType: "blob" }
                    );

                    setTopEmployeeImageUrl(URL.createObjectURL(imageResponse.data));
                }

            })
            .catch(console.log);

    }, []);

    const filteredEmployees =
        employees.filter(employee => {

            const matchesName =
                (employee.name || "")
                    .toLowerCase()
                    .includes(
                        searchName
                            .toLowerCase()
                    );

            const matchesPhone =
                (employee.phoneNumber || "")
                    .includes(
                        searchPhone
                    );

            return matchesName
                &&
                matchesPhone;
        });
    return (
        <DashboardLayout>

            <div className="page-bg">

                <div className="employees-page">

                    <div className="employees-container">

                        {/* Header */}

                        <div className="employees-header">

                            <h1 className="employees-title">

                                Employees

                            </h1>

                            <p className="employees-subtitle">

                                Track workload and performance across your team

                            </p>

                        </div>

                        {/* Employee of the Month */}

                        {dashboard && (

                            <div className="employee-highlight-card">

                                <div className="employee-highlight-content">

                                    <img
                                        src={
                                            topEmployeeImageUrl
                                                ?
                                                topEmployeeImageUrl
                                                :
                                                "/default-avatar.png"
                                        }
                                        className="employee-highlight-image"
                                    />

                                    <div>

                                        <h2 className="employee-highlight-title">

                                            🏆 Employee of the Month

                                        </h2>

                                        <h3 className="employee-highlight-name">

                                            {dashboard.employeeOfMonth?.name}

                                        </h3>

                                        <p className="employee-highlight-message">

                                            {dashboard.employeeOfMonth?.message}

                                        </p>

                                        <div className="employee-highlight-stats">

                                            <div>

                                                <h4 className="highlight-label">

                                                    Revenue

                                                </h4>

                                                <p className="highlight-value">

                                                    ₹{dashboard.employeeOfMonth?.revenue}

                                                </p>

                                            </div>

                                            <div>

                                                <h4 className="highlight-label">

                                                    Completed

                                                </h4>

                                                <p className="highlight-value">

                                                    {dashboard.employeeOfMonth?.completedTasks}

                                                </p>

                                            </div>

                                            <div>

                                                <h4 className="highlight-label">

                                                    Completion

                                                </h4>

                                                <p className="highlight-value">

                                                    {dashboard.employeeOfMonth?.completionPercentage}%

                                                </p>

                                            </div>

                                        </div>

                                    </div>

                                </div>

                            </div>

                        )}

                        {/* Revenue Chart */}

                        {dashboard && (

                            <div className="revenue-chart-card">

                                <h2 className="chart-title">

                                    Monthly Revenue Trend

                                </h2>

                                <div className="chart-container">

                                    <ResponsiveContainer>

                                        <LineChart data={dashboard.monthlyRevenue}>

                                            <CartesianGrid strokeDasharray="3 3"/>

                                            <XAxis dataKey="month"/>

                                            <YAxis/>

                                            <Tooltip/>

                                            <Line
                                                type="monotone"
                                                dataKey="revenue"
                                                stroke="#2563eb"
                                                strokeWidth={4}
                                            />

                                        </LineChart>

                                    </ResponsiveContainer>

                                </div>

                            </div>

                        )}

                        {/* Summary Cards */}

                        <div className="employee-summary-grid">

                            {filteredEmployees.map(employee => {

                                const completion =
                                    employee.taskCount > 0
                                        ? Math.round(
                                            (employee.completedTasks / employee.taskCount) * 100
                                        )
                                        : 0;

                                return (

                                    <Link
                                        key={employee.id}
                                        to={`/employees/${employee.id}`}
                                        className="employee-summary-card"
                                    >

                                        <div className="employee-card-header">

                                            <div>

                                                <h3 className="employee-name">

                                                    {employee.name}

                                                </h3>

                                                <p className="employee-phone">

                                                    {employee.phoneNumber}

                                                </p>

                                            </div>

                                            <span
                                                className={
                                                    employee.active
                                                        ? "employee-status active-status"
                                                        : "employee-status inactive-status"
                                                }
                                            >

                                            {employee.active ? "Active" : "Inactive"}

                                        </span>

                                        </div>

                                        <div className="employee-progress-info">

                                        <span>

                                            {employee.completedTasks}/{employee.taskCount} completed

                                        </span>

                                            <span className="progress-percentage">

                                            {completion}%

                                        </span>

                                        </div>

                                        <div className="progress-bar">

                                            <div
                                                className="progress-fill"
                                                style={{
                                                    width: `${completion}%`
                                                }}
                                            />

                                        </div>

                                    </Link>

                                );

                            })}

                        </div>

                        {/* Search */}

                        <div className="employee-search-grid">

                            <input
                                type="text"
                                placeholder="Search by Name"
                                value={searchName}
                                onChange={(e)=>setSearchName(e.target.value)}
                                className="employee-search-input"
                            />

                            <input
                                type="text"
                                placeholder="Search by Phone"
                                value={searchPhone}
                                onChange={(e)=>setSearchPhone(e.target.value)}
                                className="employee-search-input"
                            />

                        </div>

                        {/* Table */}

                        <div className="employee-table-card">

                            <table className="employee-table">

                                <thead className="employee-table-header">

                                <tr>

                                    <th className="table-heading">ID</th>

                                    <th className="table-heading">Name</th>

                                    <th className="table-heading">Phone</th>

                                    <th className="table-heading">Tasks</th>

                                    <th className="table-heading">Completed</th>

                                    <th className="table-heading">Status</th>

                                </tr>

                                </thead>

                                <tbody>

                                {filteredEmployees.map(employee => (

                                    <tr
                                        key={employee.id}
                                        className="employee-row"
                                    >

                                        <td className="table-cell">

                                            {employee.id}

                                        </td>

                                        <td className="table-cell employee-name-cell">

                                            {employee.name}

                                        </td>

                                        <td className="table-cell">

                                            {employee.phoneNumber}

                                        </td>

                                        <td className="table-cell">

                                            {employee.taskCount}

                                        </td>

                                        <td className="table-cell">

                                            {employee.completedTasks}

                                        </td>

                                        <td className="table-cell">

                                            <span
                                                className={
                                                    employee.active
                                                        ? "employee-status active-status"
                                                        : "employee-status inactive-status"
                                                }
                                            >

                                                {employee.active ? "Active" : "Inactive"}

                                            </span>

                                        </td>

                                    </tr>

                                ))}

                                </tbody>

                            </table>

                        </div>

                    </div>

                </div>

            </div>

        </DashboardLayout>
    );
}

export default Employees;
