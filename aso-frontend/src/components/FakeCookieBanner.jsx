import { useEffect, useState } from "react";
import "./FakeCookieBanner.css";

const STORAGE_KEY = "aso-cookie-easter-egg";

/**
 * Looks like a cookie banner. Decline does nothing useful.
 * Accept summons Psyduck for two seconds.
 */
export default function FakeCookieBanner() {
  const [visible, setVisible] = useState(() => {
    try {
      return sessionStorage.getItem(STORAGE_KEY) !== "1";
    } catch {
      return true;
    }
  });
  const [showPsyduck, setShowPsyduck] = useState(false);

  useEffect(() => {
    if (!showPsyduck) return undefined;
    const id = window.setTimeout(() => setShowPsyduck(false), 2000);
    return () => window.clearTimeout(id);
  }, [showPsyduck]);

  function dismiss() {
    try {
      sessionStorage.setItem(STORAGE_KEY, "1");
    } catch {
      /* ignore */
    }
    setVisible(false);
  }

  function accept() {
    dismiss();
    setShowPsyduck(true);
  }

  if (!visible && !showPsyduck) return null;

  return (
    <>
      {visible && (
        <div className="fake-cookie-banner" role="dialog" aria-label="Cookie notice">
          <p className="fake-cookie-copy">
            We use cookies to improve your experience on this site. You can accept or decline
            non-essential cookies.
          </p>
          <div className="fake-cookie-actions">
            <button type="button" className="fake-cookie-decline" onClick={dismiss}>
              Decline
            </button>
            <button type="button" className="fake-cookie-accept" onClick={accept}>
              Accept
            </button>
          </div>
        </div>
      )}
      {showPsyduck && (
        <div className="fake-cookie-psyduck" aria-hidden="true">
          <img src="/psyduck.jpg" alt="" />
        </div>
      )}
    </>
  );
}
