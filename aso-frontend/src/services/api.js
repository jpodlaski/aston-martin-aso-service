import axios from "axios";

// Shared Axios instance for all frontend API calls.
export const api = axios.create({
    baseURL: "http://localhost:8080",
});
