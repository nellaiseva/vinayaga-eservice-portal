import { useEffect, useState } from "react";
import axios from "axios";
import CustomerNavbar from "../../components/CustomerNavbar";
import { useNavigate } from "react-router-dom";
import { API_URL } from "../../config";
import {
    getActiveCategories
} from "../../services/serviceCategoryService";
import "./CustomerService.css";
import LoadingScreen from "../../components/LoadingScreen";
import Pagination from "../../components/Pagination";

function CustomerServices() {

    const [services, setServices] =
        useState([]);
    const [categories, setCategories] = useState([]);

    const [selectedCategory,
        setSelectedCategory] = useState(null);
    const [searchTerm, setSearchTerm] = useState("");
    const [page, setPage] = useState(0);

    const [size, setSize] = useState(9);
    const [debouncedSearch, setDebouncedSearch] = useState(searchTerm);
    const [totalPages, setTotalPages] = useState(0);
    const [loading, setLoading] = useState(false);
    const [totalElements, setTotalElements] = useState(0);
    const navigate =
        useNavigate();

    useEffect(() => {

        const timer = setTimeout(() => {

            setDebouncedSearch(searchTerm);

            setPage(0);

        }, 400);

        return () => clearTimeout(timer);

    }, [searchTerm]);
    const loadData = async () => {

        setLoading(true);

        try {

            const params = new URLSearchParams();

            params.append("page", page);
            params.append("size", size);

            if (debouncedSearch.trim()) {
                params.append(
                    "search",
                    debouncedSearch.trim()
                );
            }

            const serviceResponse =
                await axios.get(
                    `${API_URL}/services?${params.toString()}`
                );

            console.log(serviceResponse.data);

            setServices(serviceResponse.data.content);
            setTotalPages(serviceResponse.data.totalPages);
            setTotalElements(serviceResponse.data.totalElements);

            const categoryResponse =
                await getActiveCategories();

            setCategories(categoryResponse.data);

        } catch (e) {

            console.error(e);

        } finally {

            setLoading(false);

        }
    };
    useEffect(() => {

        loadData();

    }, [

        page,

        size,

        debouncedSearch

    ]);
    if (loading) {
        return <LoadingScreen message="Loading services..." />;
    }

    return (
        <>
            <CustomerNavbar />

            <div className="page-bg">

                <div className="services-page">

                    <div className="services-container">

                        {/* Header */}

                        <div className="services-header">

                            <div className="services-search">

                            <span className="search-icon">
                                🔍
                            </span>

                                <input
                                    type="text"
                                    placeholder="Search certificates and services..."
                                    value={searchTerm}
                                    onChange={(e) =>
                                        setSearchTerm(e.target.value)
                                    }
                                    className="search-input"
                                />

                            </div>

                            <h1 className="services-title">

                                Available Services

                            </h1>

                            <p className="services-subtitle">

                                Choose a service and begin your application process.

                            </p>

                            <div className="category-list">

                                <button

                                    onClick={() =>
                                        setSelectedCategory(null)
                                    }

                                    className={`category-button ${
                                        selectedCategory == null
                                            ? "category-active"
                                            : ""
                                    }`}
                                >

                                    All

                                </button>

                                {

                                    categories.map(category => (

                                        <button

                                            key={category.id}

                                            onClick={() =>
                                                setSelectedCategory(category)
                                            }

                                            className={`category-button ${
                                                selectedCategory?.id === category.id
                                                    ? "category-active"
                                                    : ""
                                            }`}
                                        >

                                            {category.name}

                                        </button>

                                    ))

                                }

                            </div>

                        </div>

                        {/* Services Grid */}

                        <div className="services-grid">

                            {services
                                .filter(service => {

                                    if (selectedCategory == null) {

                                        return true;

                                    }

                                    return selectedCategory.serviceIds?.includes(service.id) || false;

                                })
                                .map(service => (

                                    <div
                                        key={service.id}
                                        className="service-card"
                                    >

                                        <div className="service-card-header">

                                            <div className="service-icon">

                                                📄

                                            </div>

                                            <span className="service-status">

                                            Active

                                        </span>

                                        </div>

                                        <div className="service-card-body">
                                            <h3 className="service-title">{service.serviceName}</h3>

                                            <p className="service-description">
                                                {service.description}
                                            </p>
                                        </div>

                                        <button
                                            onClick={() =>
                                                navigate(
                                                    `/service-documents/${service.id}`
                                                )
                                            }
                                            className="apply-button"
                                        >

                                            Apply Now →

                                        </button>

                                    </div>

                                ))}

                        </div>

                    </div>

                    <div className="services-pagination">

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

export default CustomerServices;