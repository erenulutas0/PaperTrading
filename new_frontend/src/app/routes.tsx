import { createBrowserRouter } from "react-router";
import { RootLayout } from "./components/layout/RootLayout";
import { Landing } from "./pages/Landing";
import { Login } from "./pages/Login";
import { Register } from "./pages/Register";
import { Dashboard } from "./pages/Dashboard";
import { PortfolioDetail } from "./pages/PortfolioDetail";
import { AnalysisHub } from "./pages/AnalysisHub";
import { AnalysisDetail } from "./pages/AnalysisDetail";
import { NewAnalysis } from "./pages/NewAnalysis";
import { DiscoverPortfolios } from "./pages/DiscoverPortfolios";
import { Leaderboard } from "./pages/Leaderboard";
import { TournamentsList } from "./pages/TournamentsList";
import { TournamentLiveHub } from "./pages/TournamentLiveHub";
import { UserProfile } from "./pages/UserProfile";
import { Notifications } from "./pages/Notifications";
import { Watchlist } from "./pages/Watchlist";
import { DesignSystem } from "./pages/DesignSystem";

export const router = createBrowserRouter([
  {
    path: "/",
    Component: Landing,
  },
  {
    path: "/login",
    Component: Login,
  },
  {
    path: "/register",
    Component: Register,
  },
  {
    path: "/design-system",
    Component: DesignSystem,
  },
  {
    element: <RootLayout />,
    children: [
      {
        path: "/dashboard",
        Component: Dashboard,
      },
      {
        path: "/portfolio/:id",
        Component: PortfolioDetail,
      },
      {
        path: "/analysis",
        Component: AnalysisHub,
      },
      {
        path: "/analysis/:id",
        Component: AnalysisDetail,
      },
      {
        path: "/analysis/new",
        Component: NewAnalysis,
      },
      {
        path: "/discover",
        Component: DiscoverPortfolios,
      },
      {
        path: "/leaderboard",
        Component: Leaderboard,
      },
      {
        path: "/tournaments",
        Component: TournamentsList,
      },
      {
        path: "/tournament/:id",
        Component: TournamentLiveHub,
      },
      {
        path: "/profile/:username",
        Component: UserProfile,
      },
      {
        path: "/notifications",
        Component: Notifications,
      },
      {
        path: "/watchlist",
        Component: Watchlist,
      },
    ],
  },
]);
