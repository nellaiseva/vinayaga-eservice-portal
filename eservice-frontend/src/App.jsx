import {
    BrowserRouter,
    Routes,
    Route
} from "react-router-dom";
import Forbidden from "./pages/Forbidden/Forbidden";
import Unauthorized from "./pages/Unauthorized/Unauthorized";
import CreateEmployee
    from "./pages/owner/CreateEmployee";
import Login from "./pages/auth/Login";
import Dashboard from "./pages/owner/Dashboard";
import ProtectedRoute from "./components/ProtectedRoute";
import Services from "./pages/owner/Services";
import CreateService from "./pages/owner/CreateService.jsx";
import EditService
    from "./pages/owner/EditService";
import CreateRequest
    from "./pages/customer/CreateRequest";
import Requests
    from "./pages/owner/Requests";
import EmployeeDashboard from "./pages/employee/EmployeeDashboard";
import MyRequests from "./pages/customer/MyRequests";
import Employees from "./pages/owner/Employees";
import ServiceDocuments
    from "./pages/customer/ServiceDocuments";
import CustomerServices
    from "./pages/customer/CustomerServices";
import Users
    from "./pages/owner/Users";
import LandingPage from "./pages/LandingPage";
import CustomerLogin
    from "./pages/customer/CustomerLogin";
import CustomerProfile
    from "./pages/customer/CustomerProfile";

import CustomerProfileCheck
    from "./pages/customer/CustomerProfileCheck";
import CustomerProtectedRoute
    from "./components/CustomerProtectedRoute";
import CustomerProfileView
    from "./pages/customer/CustomerProfileView";

import CustomerProfileEdit
    from "./pages/customer/CustomerProfileEdit";
import { AnimatePresence }
    from "framer-motion";

import {
    useLocation
} from "react-router-dom";
import ServiceFieldManager
    from "./pages/owner/ServiceFieldManager";
import RequestDetails
    from "./pages/common/RequestDetails";
import EmployeePerformance
    from "./pages/owner/EmployeePerformance";
import EmployeeRequests
    from "./pages/employee/EmployeeRequests";
import EmployeeProfile from "./pages/employee/EmployeeProfile";
import EmployeeProfileEdit
    from "./pages/employee/EmployeeProfileEdit";
import ServiceCategories
    from "./pages/owner/ServiceCategories";
import NotFound from "./pages/NotFound/NotFound";
function AnimatedRoutes() {

    const location =
        useLocation();

    return (

        <AnimatePresence mode="wait">

            <Routes
                location={location}
                key={location.pathname}
            >


                    <Route
                        path="/"
                        element={<LandingPage />}
                    />
                    <Route
                        path="/login"
                        element={<Login />}
                    />
                    <Route
                        path="/customer-login"
                        element={<CustomerLogin />}
                    />


                <Route
                    path="/dashboard"
                    element={
                        <ProtectedRoute allowedRoles={["OWNER"]}>
                            <Dashboard />
                        </ProtectedRoute>
                    }
                />

                <Route
                    path="/services"
                    element={
                        <ProtectedRoute allowedRoles={["OWNER"]}>
                            <Services />
                        </ProtectedRoute>
                    }
                />

                <Route
                    path="/services/create"
                    element={
                        <ProtectedRoute allowedRoles={["OWNER"]}>
                            <CreateService />
                        </ProtectedRoute>
                    }
                />
                <Route
                    path="/services/edit/:id"
                    element={
                        <ProtectedRoute allowedRoles={["OWNER"]}>
                            <EditService />
                        </ProtectedRoute>
                    }
                />

                    <Route
                        path="/requests/create"
                        element={
                            <CustomerProtectedRoute>
                                <CreateRequest />
                            </CustomerProtectedRoute>
                        }
                    />
                <Route
                    path="/requests"
                    element={
                        <ProtectedRoute allowedRoles={["OWNER"]}>
                            <Requests />
                        </ProtectedRoute>
                    }
                />
                <Route
                    path="/employee-dashboard"
                    element={
                        <ProtectedRoute
                            allowedRoles={["EMPLOYEE", "OWNER"]}
                        >
                            <EmployeeDashboard />
                        </ProtectedRoute>
                    }
                />
                    <Route
                        path="/my-requests"
                        element={
                            <CustomerProtectedRoute>
                                <MyRequests />
                            </CustomerProtectedRoute>
                        }
                    />
                <Route
                    path="/employees"
                    element={
                        <ProtectedRoute allowedRoles={["OWNER"]}>
                            <Employees />
                        </ProtectedRoute>
                    }
                />

                <Route
                    path="/users"
                    element={
                        <ProtectedRoute allowedRoles={["OWNER"]}>
                            <Users />
                        </ProtectedRoute>
                    }
                />


                    <Route
                        path="/service-documents/:id"
                        element={
                            <CustomerProtectedRoute>
                                <ServiceDocuments />
                            </CustomerProtectedRoute>
                        }
                    />

                    <Route
                        path="/customer-services"
                        element={
                            <CustomerProtectedRoute>
                                <CustomerServices />
                            </CustomerProtectedRoute>
                        }
                    />
                    <Route
                        path="/customer-profile"
                        element={
                            <CustomerProtectedRoute>
                                <CustomerProfile />
                            </CustomerProtectedRoute>
                        }
                    />
                    <Route
                        path="/customer-profile-check"
                        element={
                            <CustomerProtectedRoute>
                                <CustomerProfileCheck />
                            </CustomerProtectedRoute>
                        }
                    />
                    <Route
                        path="/customer-profile-view"
                        element={
                            <CustomerProtectedRoute>
                                <CustomerProfileView />
                            </CustomerProtectedRoute>
                        }
                    />

                    <Route
                        path="/customer-profile-edit"
                        element={
                            <CustomerProtectedRoute>
                                <CustomerProfileEdit />
                            </CustomerProtectedRoute>
                        }
                    />
                <Route
                    path="/service-fields/:serviceId"
                    element={
                        <ProtectedRoute allowedRoles={["OWNER"]}>
                            <ServiceFieldManager />
                        </ProtectedRoute>
                    }
                />
                <Route
                    path="/request-details/:id"
                    element={
                        <ProtectedRoute
                            allowedRoles={["CUSTOMER", "EMPLOYEE", "OWNER"]}
                        >
                            <RequestDetails />
                        </ProtectedRoute>
                    }
                />
                <Route
                    path="/employee-requests"
                    element={
                        <ProtectedRoute
                            allowedRoles={["EMPLOYEE", "OWNER"]}
                        >
                            <EmployeeRequests />
                        </ProtectedRoute>
                    }
                />
                <Route
                    path="/employees/:id"
                    element={
                        <ProtectedRoute allowedRoles={["OWNER"]}>
                            <EmployeePerformance />
                        </ProtectedRoute>
                    }
                />
                <Route
                    path="/employee/profile"
                    element={
                        <ProtectedRoute allowedRoles={["EMPLOYEE", "OWNER"]}>
                            <EmployeeProfile />
                        </ProtectedRoute>
                    }
                />
                <Route
                    path="/employee/profile/edit"
                    element={
                        <ProtectedRoute allowedRoles={["EMPLOYEE", "OWNER"]}>
                            <EmployeeProfileEdit />
                        </ProtectedRoute>
                    }
                />
                <Route

                    path="/service-categories"
                    element={
                        <ProtectedRoute allowedRoles={["OWNER"]}>
                            <ServiceCategories />
                        </ProtectedRoute>
                    }
                />
                <Route
                    path="/employees/create"
                    element={
                        <ProtectedRoute allowedRoles={["OWNER"]}>
                            <CreateEmployee />
                        </ProtectedRoute>
                    }
                />
                <Route
                    path="/401"
                    element={<Unauthorized />}
                />

                <Route
                    path="/403"
                    element={<Forbidden />}
                />

                <Route
                    path="*"
                    element={<NotFound />}
                />
            </Routes>

        </AnimatePresence>
    );
}
function App() {

    return (
        <BrowserRouter>

            <AnimatedRoutes />

        </BrowserRouter>
    );
}

export default App;