import { useNavigate } from "react-router-dom";
import { motion } from "framer-motion";
import "./Forbidden.css";

function Forbidden() {

    const navigate = useNavigate();

    const role = localStorage.getItem("role");

    const goBack = () => {

        if (role === "OWNER") {
            navigate("/dashboard");
        }
        else if (role === "EMPLOYEE") {
            navigate("/employee-dashboard");
        }
        else {
            navigate("/");
        }
    };

    return (
        <div className="forbidden-page">

            <motion.div
                className="forbidden-card"
                initial={{ opacity: 0, y: 30 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.5 }}
            >

                <div className="forbidden-icon">
                    🔒
                </div>

                <div className="forbidden-code">
                    403
                </div>

                <h1>
                    Access Denied
                </h1>

                <p>
                    You don't have permission to access
                    this page.
                </p>

                <button
                    className="forbidden-btn"
                    onClick={goBack}
                >
                    ← Go Back
                </button>

            </motion.div>

        </div>
    );
}

export default Forbidden;