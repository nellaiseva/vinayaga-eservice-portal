import { motion } from "framer-motion";
import "./LoadingScreen.css";

function LoadingScreen({ message = "Loading..." }) {

    return (
        <div className="loading-screen">

            <motion.div
                className="loading-card"
                initial={{ opacity: 0, scale: 0.95 }}
                animate={{ opacity: 1, scale: 1 }}
                transition={{ duration: 0.4 }}
            >

                <motion.div
                    className="loading-logo"
                    animate={{
                        scale: [1, 1.05, 1]
                    }}
                    transition={{
                        duration: 1.8,
                        repeat: Infinity
                    }}
                >
                    🏛️
                </motion.div>

                <h1 className="loading-title">
                    NellaieSeva
                </h1>

                <p className="loading-subtitle">
                    Government Services Portal
                </p>

                <div className="loading-spinner"></div>

                <p className="loading-message">
                    {message}
                </p>

                <div className="loading-dots">
                    <span></span>
                    <span></span>
                    <span></span>
                </div>

            </motion.div>

        </div>
    );
}

export default LoadingScreen;