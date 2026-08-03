import { BrowserRouter, Routes, Route } from "react-router-dom";

import Login from "./pages/Login";
import Register from "./pages/Register";
import ForgotPassword from "./pages/ForgotPassword";
import ResetPassword from "./pages/ResetPassword";
import VerifyEmail from "./pages/VerifyEmail";
import ResendVerification from "./pages/ResendVerification";
import EmployeeLogin from "./pages/EmployeeLogin";
import AddVehicle from "./pages/AddVehicle";
import ClientDashboard from "./pages/ClientDashboard";
import RequestService from "./pages/RequestService";
import EmployeeDashboard from "./pages/EmployeeDashboard";
import AdminDashboard from "./pages/AdminDashboard";
import PrivateRoute from "./components/PrivateRoute";
import { MANAGEMENT_ROLES, WORKSHOP_ROLES } from "./constants/roles";

/**
 * Front-door routing for the three user worlds:
 * - CLIENT → /client*
 * - workshop staff → /employee
 * - management → /admin
 * Public login/register routes stay open; everything else wraps PrivateRoute (session + role).
 */
export default function App() {
  return (
      <BrowserRouter>
        <Routes>
          <Route path="/" element={<Login />} />
          <Route path="/register" element={<Register />} />
          <Route path="/forgot-password" element={<ForgotPassword />} />
          <Route path="/reset-password" element={<ResetPassword />} />
          <Route path="/verify-email" element={<VerifyEmail />} />
          <Route path="/resend-verification" element={<ResendVerification />} />
          <Route path="/employee-login" element={<EmployeeLogin />} />
          <Route
              path="/client"
              element={
                <PrivateRoute roles={["CLIENT"]}>
                  <ClientDashboard />
                </PrivateRoute>
              }
          />
          <Route
              path="/client/add-vehicle"
              element={
                <PrivateRoute roles={["CLIENT"]}>
                  <AddVehicle />
                </PrivateRoute>
              }
          />
          <Route
              path="/client/request-service"
              element={
                <PrivateRoute roles={["CLIENT"]}>
                  <RequestService />
                </PrivateRoute>
              }
          />
          <Route
              path="/employee"
              element={
                <PrivateRoute roles={WORKSHOP_ROLES}>
                  <EmployeeDashboard />
                </PrivateRoute>
              }
          />
          <Route
              path="/admin"
              element={
                <PrivateRoute roles={MANAGEMENT_ROLES}>
                  <AdminDashboard />
                </PrivateRoute>
              }
          />
        </Routes>
      </BrowserRouter>
  );
}
