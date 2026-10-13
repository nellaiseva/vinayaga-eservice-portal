import { useEffect, useState, useMemo } from "react";
import axios from "axios";
import { API_URL } from "../../config";
import DashboardLayout from "../../layouts/DashboardLayout";
import "./Users.css";
import { Link } from "react-router-dom";

const FILTERS = [
    { id: "ALL", label: "All Users" },
    { id: "OWNER", label: "Owners" },
    { id: "EMPLOYEE", label: "Employees" },
    { id: "CUSTOMER", label: "Customers" },
];

const isValidName = (name) => {
    return typeof name === "string" && name.trim().length > 0;
};

const getRoleRank = (role) => {
    const r = (role || "").toUpperCase();
    if (r === "OWNER") return 1;
    if (r === "EMPLOYEE") return 2;
    if (r === "CUSTOMER") return 3;
    return 4;
};

const compareUsers = (a, b) => {
    // 1. Order by role: OWNER -> EMPLOYEE -> CUSTOMER
    const roleRankA = getRoleRank(a?.role);
    const roleRankB = getRoleRank(b?.role);
    if (roleRankA !== roleRankB) {
        return roleRankA - roleRankB;
    }

    // 2. Within each role: valid names first (sorted alphabetically A-Z), missing names after
    const aHasName = isValidName(a?.name);
    const bHasName = isValidName(b?.name);

    if (aHasName && bHasName) {
        const cmp = a.name.trim().localeCompare(b.name.trim(), undefined, {
            sensitivity: "base",
            numeric: true,
        });
        if (cmp !== 0) return cmp;
        return a.name.trim().localeCompare(b.name.trim());
    }
    if (aHasName && !bHasName) return -1;
    if (!aHasName && bHasName) return 1;

    // 3. Fallback to ID for deterministic sorting if both have missing names
    if (a?.id && b?.id && a.id !== b.id) {
        return a.id - b.id;
    }
    return 0;
};

const getDisplayName = (user) => {
    if (isValidName(user?.name)) {
        return user.name.trim();
    }
    return "Name Not Provided";
};

const getAvatarInitials = (user) => {
    if (isValidName(user?.name)) {
        return user.name.trim().substring(0, 2).toUpperCase();
    }
    return "—";
};

const getFilterSummaryLabel = (filter, count) => {
    switch (filter) {
        case "OWNER":
            return count === 1 ? "Owner" : "Owners";
        case "EMPLOYEE":
            return count === 1 ? "Employee" : "Employees";
        case "CUSTOMER":
            return count === 1 ? "Customer" : "Customers";
        default:
            return count === 1 ? "User" : "Users";
    }
};

function Users() {
    const [users, setUsers] = useState([]);
    const [activeFilter, setActiveFilter] = useState("ALL");
    const [isLoading, setIsLoading] = useState(true);

    const loadUsers = () => {
        const token = localStorage.getItem("token");

        axios
            .get(`${API_URL}/users`, {
                headers: {
                    Authorization: `Bearer ${token}`,
                },
            })
            .then((res) => {
                setUsers(Array.isArray(res.data) ? res.data : []);
            })
            .catch((err) => {
                console.error("Failed to load users:", err);
            })
            .finally(() => {
                setIsLoading(false);
            });
    };

    useEffect(() => {
        loadUsers();
    }, []);

    const filterCounts = useMemo(() => {
        const total = users.length;
        const owners = users.filter(
            (u) => (u.role || "").toUpperCase() === "OWNER"
        ).length;
        const employees = users.filter(
            (u) => (u.role || "").toUpperCase() === "EMPLOYEE"
        ).length;
        const customers = users.filter(
            (u) => (u.role || "").toUpperCase() === "CUSTOMER"
        ).length;

        return {
            ALL: total,
            OWNER: owners,
            EMPLOYEE: employees,
            CUSTOMER: customers,
        };
    }, [users]);

    const displayedUsers = useMemo(() => {
        const filtered = users.filter((u) => {
            if (activeFilter === "ALL") return true;
            return (u.role || "").toUpperCase() === activeFilter;
        });

        return [...filtered].sort(compareUsers);
    }, [users, activeFilter]);

    return (
        <DashboardLayout>
            <div className="page-bg">
                <div className="users-page">
                    <div className="users-container">
                        {/* Header */}
                        <div className="users-header">
                            <div>
                                <h1 className="users-title">Users</h1>
                                <p className="users-subtitle">
                                    Manage roles and access across the portal
                                </p>
                            </div>

                            <Link
                                to="/employees/create"
                                className="vm-create-btn"
                            >
                                + Add Employee
                            </Link>
                        </div>

                        {/* Filter Buttons & Total Count */}
                        <div className="users-controls-bar">
                            <div
                                className="users-filter-group"
                                role="tablist"
                                aria-label="Filter users by role"
                            >
                                {FILTERS.map((tab) => {
                                    const isActive = activeFilter === tab.id;
                                    return (
                                        <button
                                            key={tab.id}
                                            type="button"
                                            role="tab"
                                            aria-selected={isActive}
                                            className={`users-filter-btn ${
                                                isActive ? "active" : ""
                                            }`}
                                            onClick={() =>
                                                setActiveFilter(tab.id)
                                            }
                                        >
                                            <span className="users-filter-label">
                                                {tab.label}
                                            </span>
                                            <span className="users-filter-badge">
                                                {filterCounts[tab.id]}
                                            </span>
                                        </button>
                                    );
                                })}
                            </div>

                            <div className="users-count-summary">
                                Total: <strong>{displayedUsers.length}</strong>{" "}
                                {getFilterSummaryLabel(
                                    activeFilter,
                                    displayedUsers.length
                                )}
                            </div>
                        </div>

                        {/* Users Table */}
                        <div className="users-table-card">
                            <table className="users-table">
                                <thead className="users-table-header">
                                    <tr>
                                        <th className="table-heading">Name</th>
                                        <th className="table-heading">Phone</th>
                                        <th className="table-heading">Role</th>
                                    </tr>
                                </thead>

                                <tbody>
                                    {isLoading ? (
                                        <tr>
                                            <td
                                                colSpan="3"
                                                className="users-empty-cell"
                                            >
                                                <div className="users-empty-state">
                                                    <p className="users-empty-title">
                                                        Loading users...
                                                    </p>
                                                </div>
                                            </td>
                                        </tr>
                                    ) : displayedUsers.length === 0 ? (
                                        <tr>
                                            <td
                                                colSpan="3"
                                                className="users-empty-cell"
                                            >
                                                <div className="users-empty-state">
                                                    <div className="users-empty-icon">
                                                        👥
                                                    </div>
                                                    <p className="users-empty-title">
                                                        {activeFilter ===
                                                        "OWNER"
                                                            ? "No owners found"
                                                            : activeFilter ===
                                                              "EMPLOYEE"
                                                            ? "No employees found"
                                                            : activeFilter ===
                                                              "CUSTOMER"
                                                            ? "No customers found"
                                                            : "No users found"}
                                                    </p>
                                                    <p className="users-empty-subtitle">
                                                        There are no users to
                                                        display for this filter.
                                                    </p>
                                                </div>
                                            </td>
                                        </tr>
                                    ) : (
                                        displayedUsers.map((user) => (
                                            <tr
                                                key={user.id}
                                                className="user-row"
                                            >
                                                <td className="table-cell">
                                                    <div className="user-info">
                                                        <div className="user-avatar">
                                                            {getAvatarInitials(
                                                                user
                                                            )}
                                                        </div>
                                                        <span
                                                            className={`user-name ${
                                                                !isValidName(
                                                                    user.name
                                                                )
                                                                    ? "user-name-missing"
                                                                    : ""
                                                            }`}
                                                        >
                                                            {getDisplayName(
                                                                user
                                                            )}
                                                        </span>
                                                    </div>
                                                </td>

                                                <td className="table-cell user-phone">
                                                    {user.phoneNumber}
                                                </td>

                                                <td className="table-cell">
                                                    <span
                                                        className={
                                                            user.role ===
                                                            "OWNER"
                                                                ? "role-badge owner-role"
                                                                : user.role ===
                                                                  "EMPLOYEE"
                                                                ? "role-badge employee-role"
                                                                : "role-badge customer-role"
                                                        }
                                                    >
                                                        {user.role}
                                                    </span>
                                                </td>
                                            </tr>
                                        ))
                                    )}
                                </tbody>
                            </table>
                        </div>
                    </div>
                </div>
            </div>
        </DashboardLayout>
    );
}

export default Users;