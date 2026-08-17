import { useEffect, useState } from "react";
import axios from "axios";
import Navbar from "../../components/Navbar";
import { API_URL } from "../../config";
import { Link } from "react-router-dom";
import DashboardLayout
    from "../../layouts/DashboardLayout";
import Pagination from "../../components/Pagination";
import "./Services.css";
import LoadingScreen from "../../components/LoadingScreen";
function Services() {

    const [services, setServices] = useState([]);

    const [page, setPage] = useState(0);

    const [size, setSize] = useState(10);

    const [totalPages, setTotalPages] = useState(0);
    const [loading, setLoading] = useState(false);
    const [totalElements, setTotalElements] = useState(0);
    const loadServices = async () => {

        setLoading(true);

        try {

            const token = localStorage.getItem("token");

            const response = await axios.get(
                `${API_URL}/admin/services?page=${page}&size=${size}`,
                {
                    headers: {
                        Authorization: `Bearer ${token}`
                    }
                }
            );

            console.log(response.data);

            setServices(response.data.content);
            setTotalPages(response.data.totalPages);
            setTotalElements(response.data.totalElements);

        } catch (error) {

            console.error(error);

        } finally {

            setLoading(false);

        }
    };
    useEffect(() => {

        loadServices()
            .catch(console.error);

    }, [page, size]);
    const deleteService = async (id) => {
        try {

            const token = localStorage.getItem("token");

            await axios.delete(
                `${API_URL}/admin/services/${id}`,
                {
                    headers: {
                        Authorization: `Bearer ${token}`
                    }
                }
            );

            if (
                services.length === 1 &&
                page > 0
            ) {
                setPage(page - 1);
                return;
            }

            await loadServices();

            alert(
                "Service deleted successfully. If the service had existing requests, it has been deactivated instead."
            );

        } catch (error) {

            console.log(error.response);
            console.log(error.response?.data);
            console.log(error.response?.status);

            alert(
                error.response?.data || "Delete failed."
            );

        }

    };
    if (loading) {
        return <LoadingScreen message="Loading services..." />;
    }
    return (
        <DashboardLayout>

            <div className="vm-page-bg">

                <div className="vm-services-page">

                    {/* Header */}

                    <div className="vm-services-header">

                        <div>

                            <h1 className="vm-services-title">
                                Services
                            </h1>

                            <p className="vm-services-subtitle">
                                Manage the services available in the portal
                            </p>

                        </div>

                        <Link
                            to="/services/create"
                            className="vm-create-btn"
                        >
                            + Create Service
                        </Link>

                    </div>

                    {/* Table */}

                    <div className="vm-services-card">

                        <table className="vm-services-table">

                            <thead>

                            <tr>

                                <th>ID</th>

                                <th>Service</th>

                                <th>Description</th>

                                <th>Status</th>

                                <th className="text-end">
                                    Actions
                                </th>

                            </tr>

                            </thead>

                            <tbody>

                            {services.map(service => (

                                <tr key={service.id}>

                                    <td className="vm-id">
                                        {service.id}
                                    </td>

                                    <td>

                                        <div className="vm-service-info">

                                            <div className="vm-service-icon">
                                                📄
                                            </div>

                                            <span className="vm-service-name">
                                            {service.serviceName}
                                        </span>

                                        </div>

                                    </td>

                                    <td className="vm-description">
                                        {service.description}
                                    </td>

                                    <td>

                                        {service.active ? (

                                            <span className="vm-status active">
                                            ● Active
                                        </span>

                                        ) : (

                                            <span className="vm-status inactive">
                                            ● Inactive
                                        </span>

                                        )}

                                    </td>

                                    <td>

                                        <div className="vm-actions">

                                            <Link
                                                to={`/services/edit/${service.id}`}
                                                className="vm-action edit"
                                            >
                                                ✏️
                                            </Link>

                                            <Link
                                                to={`/service-fields/${service.id}`}
                                                className="vm-action settings"
                                            >
                                                ⚙️
                                            </Link>

                                            <Link
                                                to={`/service-documents/${service.id}`}
                                                className="vm-action view"
                                            >
                                                ↗
                                            </Link>

                                            <button
                                                className="vm-action delete"
                                                onClick={() =>
                                                    deleteService(service.id)
                                                }
                                            >
                                                🗑️
                                            </button>

                                        </div>

                                    </td>

                                </tr>

                            ))}

                            </tbody>

                        </table>

                        <Pagination
                            page={page}
                            totalPages={totalPages}
                            totalElements={totalElements}
                            pageSize={size}
                            onPageChange={setPage}
                            onPageSizeChange={setSize}
                        />

                    </div>

                </div>

            </div>

        </DashboardLayout>
    );
}

export default Services;