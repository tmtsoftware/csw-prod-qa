import {AuthContextProvider, EventKey, Event, EventName, LocationService, Prefix, Subsystem} from '@tmtsoftware/esw-ts'
import React, {useEffect, useState} from 'react'
import { BrowserRouter as Router } from 'react-router-dom'
import 'antd/dist/antd.css'
import { MenuBar } from './components/menu/MenuBar'
import { AppConfig } from './config/AppConfig'
import { LocationServiceProvider } from './contexts/LocationServiceContext'
import { useQuery } from './hooks/useQuery'
import { Routes } from './routes/Routes'
import {EventService, SystemEvent} from "@tmtsoftware/esw-ts";
import {Typography} from "antd"

const {Text} = Typography;

const basename =
  import.meta.env.NODE_ENV === 'production' ? AppConfig.applicationName : ''

export const App = (): JSX.Element => {
  const { data: locationService, loading, error } = useQuery(LocationService)
  const [eventService, setEventService] = useState<EventService | undefined>(undefined)
  const [hasError, setHasError] = useState<string>("")

  async function findEventService() {
    try {
      setEventService(await EventService())
    } catch {
      setHasError("Can't locate the Event Service.")
    }
  }

  const onEventCallback = (event: Event) => {
    const systemEvent = event as SystemEvent
    console.log("XXX Received event: ", systemEvent)
  }

    useEffect(() => {
    // noinspection JSIgnoredPromiseFromCall
    findEventService()
  }, []);


  if (loading) return <div>Loading...</div>
  if (error || !locationService)
    return <div>Location Service not Available, reason {error?.message}</div>

  if (hasError.length != 0) return (
    <Text strong={true} type="danger">{hasError}</Text>
  )

  const subsystem: Subsystem = "CSW"
  const sourcePrefix = new Prefix(subsystem, "testComp")
  const eventKey = new EventKey(sourcePrefix, new EventName("testEvent"))
  const eventKeys = new Set([eventKey])
  if (eventService) {
    eventService.subscribe(eventKeys, 1)(onEventCallback)
  }

  return (
    <div>
      <LocationServiceProvider locationService={locationService}>
        <AuthContextProvider>
          <Router basename={basename}>
            <MenuBar />
            <Routes />
          </Router>
        </AuthContextProvider>
      </LocationServiceProvider>
    </div>
  )
}
