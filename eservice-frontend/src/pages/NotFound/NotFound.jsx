import { useNavigate } from "react-router-dom";
import { motion } from "framer-motion";
import "./NotFound.css";

function NotFound() {

    const navigate = useNavigate();

    return (
        <div className="not-found-page">

            <motion.div
                className="not-found-card"
                initial={{
                    opacity: 0,
                    y: 30
                }}
                animate={{
                    opacity: 1,
                    y: 0
                }}
                transition={{
                    duration: 0.5
                }}
            >

                <motion.div
                    className="not-found-icon"
                    animate={{
                        y: [0, -6, 0]
                    }}
                    transition={{
                        duration: 2,
                        repeat: Infinity
                    }}
                >
                    🏛️
                </motion.div>

                <div className="not-found-code">
                    404
                </div>

                <h1>
                    Page Not Found
                </h1>

                <p>
                    Sorry, the page you're looking for
                    doesn't exist or may have been moved.
                </p>

                <button
                    className="not-found-home-btn"
                    onClick={() => navigate("/")}
                >
                    ← Back to Home
                </button>

            </motion.div>

        </div>
    );
}

export default NotFound;