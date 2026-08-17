import { useNavigate } from "react-router-dom";
import { motion } from "framer-motion";
import "./Unauthorized.css";

function Unauthorized() {

    const navigate = useNavigate();

    const handleLogin = () => {

        localStorage.clear();

        navigate("/login");
    };

    return (
        <div className="unauthorized-page">

            <motion.div
                className="unauthorized-card"
                initial={{ opacity: 0, y: 30 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.5 }}
            >

                <div className="unauthorized-icon">
                    🔐
                </div>

                <div className="unauthorized-code">
                    401
                </div>

                <h1>
                    Session Required
                </h1>

                <p>
                    Your session has expired or you are
                    not logged in. Please login to continue.
                </p>

                <button
                    className="unauthorized-btn"
                    onClick={handleLogin}
                >
                    Go to Staff Login →
                </button>

            </motion.div>

        </div>
    );
}

export default Unauthorized;