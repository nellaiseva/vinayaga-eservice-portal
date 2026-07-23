import axios from "axios";
import API_URL from "../config/api";

const secureApi = axios.create({
    baseURL: API_URL,
});

secureApi.interceptors.request.use((config) => {

    const token = localStorage.getItem("token");

    if (token) {
        config.headers.Authorization = `Bearer ${token}`;
    }

    return config;
});

secureApi.interceptors.response.use(
    response => response,
    error => {

        if (error.response?.status === 401 ||
            error.response?.status === 403) {

            localStorage.removeItem("token");
            localStorage.removeItem("role");

            window.location.href = "/login";
        }

        return Promise.reject(error);
    }
);

export default secureApi;