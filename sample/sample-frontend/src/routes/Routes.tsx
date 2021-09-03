import React from 'react'
import { Route, Switch } from 'react-router-dom'
import { NotFound } from '../components/error/NotFound'
import { AdminGreeting } from '../components/pages/AdminGreeting'
import { Greeting } from '../components/pages/Greeting'
import { Welcome } from '../components/pages/Welcome'
import { ProtectedRoute } from './ProtectedRoute'

export const Routes = (): JSX.Element => {
  return (
    <Switch>
      <Route exact path='/' component={Welcome} />
      <Route path='/greeting' component={Greeting} />
      <ProtectedRoute path='/adminGreeting' component={AdminGreeting} />
      <Route path='*' component={NotFound} />
    </Switch>
  )
}

